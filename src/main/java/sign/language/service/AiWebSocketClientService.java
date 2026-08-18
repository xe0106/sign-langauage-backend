package sign.language.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import sign.language.dto.AiFeatureMessage;
import sign.language.dto.SignalMessage;
import sign.language.request.CallSubtitleRequest;
import sign.language.response.CallSubtitleResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spring -> AI 서버(ws://3.107.177.191:8000/ws/inference) 웹소켓 연동 클라이언트 서비스
 *
 * 1. Android -> Spring으로 수신된 258개 MediaPipe 랜드마크 특징 데이터(features)를 AI 서버로 실시간 릴레이 전송
 * 2. AI 서버가 추론한 수어 자막 결과(prediction)를 DB에 저장하고 통화방(/sub/call/{callId})으로 실시간 브로드캐스트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiWebSocketClientService extends TextWebSocketHandler {

    @Value("${ai.websocket.url:ws://3.107.177.191:8000/ws/inference}")
    private String aiWebsocketUri;

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final CallService callService;

    private WebSocketSession aiSession;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();

    // 통화방(callId)별 최근 랜드마크를 보낸 발신자 ID(senderId) 매핑 (1L 고정 방지)
    private final Map<String, Long> lastSenderIdMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        connectToAiServer();
    }

    /**
     * AI 서버로 웹소켓 연결 시도
     */
    public synchronized void connectToAiServer() {
        if (aiSession != null && aiSession.isOpen()) {
            return;
        }

        try {
            StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
            webSocketClient.doHandshake(this, new WebSocketHttpHeaders(), URI.create(aiWebsocketUri));
            log.info("🟢 [AI WebSocket] Connecting to AI Server: {}", aiWebsocketUri);
        } catch (Exception e) {
            log.error("🔴 [AI WebSocket] Failed to connect to AI Server: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 동시 전송 시 세션 충돌 방지를 위한 스레드 안전 래퍼 적용 (전송 제한 5초, 버퍼 512KB)
        this.aiSession = new ConcurrentWebSocketSessionDecorator(session, 5000, 512 * 1024);
        log.info("🟢 [AI WebSocket] Successfully connected to AI Server: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("⚠️ [AI WebSocket] Connection closed: {}. Scheduling reconnect...", status);
        this.aiSession = null;
        scheduleReconnect();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("🔴 [AI WebSocket] Transport error: {}", exception.getMessage());
    }

    /**
     * 연결 끊김 시 5초 후 자동 재연결 시도
     */
    private void scheduleReconnect() {
        reconnectScheduler.schedule(this::connectToAiServer, 5, TimeUnit.SECONDS);
    }

    /**
     * AI 서버와의 웹소켓 연결 여부 반환
     */
    public boolean isConnected() {
        return aiSession != null && aiSession.isOpen();
    }

    /**
     * Android -> Spring으로 수신된 특징 데이터를 AI 규격에 맞게 릴레이 전송
     */
    public void sendFeatures(AiFeatureMessage message) {
        if (message == null) return;

        if (aiSession == null || !aiSession.isOpen()) {
            log.warn("⚠️ [AI WebSocket] AI session is not connected. Attempting reconnection...");
            connectToAiServer();
            return;
        }

        try {
            // UUID 포맷 검증 및 변환
            String validSessionId = ensureValidUuid(message.getSessionId());
            String validCallId = ensureValidUuid(message.getCallId());

            // callId별 발신자 ID 보존 (AI 추론 자막 생성 시 진짜 senderId 복원용)
            if (message.getSenderId() != null) {
                lastSenderIdMap.put(validCallId, message.getSenderId());
            }

            // AI 서버 표준 스키마 6개 필드 구성
            Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
            payloadMap.put("type", "landmark_frame");
            payloadMap.put("sessionId", validSessionId);
            payloadMap.put("callId", validCallId);
            payloadMap.put("sequence", message.getSequence());
            payloadMap.put("timestampMs", message.getTimestampMs());
            payloadMap.put("features", message.getFeatures());

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            log.info("📤 [AI WebSocket Relay Sent] callId: {}, seq: {}", validCallId, message.getSequence());

            aiSession.sendMessage(new TextMessage(jsonPayload));
        } catch (IOException e) {
            log.error("🔴 [AI WebSocket] Failed to send message to AI Server: {}", e.getMessage());
        }
    }

    private String ensureValidUuid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return java.util.UUID.randomUUID().toString();
        }
        try {
            java.util.UUID.fromString(value);
            return value;
        } catch (IllegalArgumentException e) {
            return java.util.UUID.nameUUIDFromBytes(value.getBytes()).toString();
        }
    }

    /**
     * AI 서버로부터 수어 추론 결과(prediction) 또는 상태(status), 에러(error)가 도착했을 때 처리
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            log.info("📩 [AI WebSocket Received] {}", payload);

            JsonNode rootNode = objectMapper.readTree(payload);

            String type = rootNode.path("type").asText("");
            String status = rootNode.path("status").asText("");
            String label = rootNode.path("label").asText("");
            String prediction = rootNode.path("prediction").asText("");
            String callId = rootNode.path("callId").asText("");

            // 1. AI 서버 에러 응답 수신 처리
            if ("error".equalsIgnoreCase(type)) {
                String errorCode = rootNode.path("code").asText("AI_ERROR");
                String errorMsg = rootNode.path("message").asText("AI processing failed");
                log.error("🔴 [AI WebSocket Error] code: {}, message: {}", errorCode, errorMsg);
                return;
            }

            // 2. AI 서버 상태 알림 (warming_up, analyzing 등)
            if ("status".equalsIgnoreCase(type)) {
                int buffered = rootNode.path("bufferedFrames").asInt(0);
                int required = rootNode.path("requiredFrames").asInt(30);
                log.info("ℹ️ [AI Frame Status] status: {}, frames: {}/{}", status, buffered, required);
                return;
            }

            // 3. AI 추론 결과 처리
            String subtitleText = (label != null && !label.trim().isEmpty()) ? label : prediction;

            if (("prediction".equalsIgnoreCase(type) || "prediction".equalsIgnoreCase(status))
                    && subtitleText != null && !subtitleText.trim().isEmpty()
                    && !callId.isBlank()) {

                // 실제 수어를 전송했던 발화자의 senderId 복원 (없으면 기본값 1L)
                Long realSenderId = rootNode.has("senderId") 
                        ? rootNode.get("senderId").asLong() 
                        : lastSenderIdMap.getOrDefault(callId, 1L);

                // 3-1. DB에 자막 저장 및 생성된 ID/시각 획득
                CallSubtitleRequest subtitleRequest = new CallSubtitleRequest(realSenderId, subtitleText);
                CallSubtitleResponse savedSubtitle = callService.saveSubtitle(callId, subtitleRequest);

                // 3-2. 브로드캐스트용 메시지 객체 생성
                SignalMessage subtitleMessage = SignalMessage.builder()
                        .type(SignalMessage.MessageType.SUBTITLE)
                        .callId(callId)
                        .senderId(realSenderId)
                        .textContent(subtitleText)
                        .subtitleId(savedSubtitle.getSubtitleId())
                        .createdAt(savedSubtitle.getCreatedAt())
                        .build();

                // 3-3. 통화방 구독자들(/sub/call/{callId})에게 실시간 자막 브로드캐스트
                messagingTemplate.convertAndSend("/sub/call/" + callId, subtitleMessage);
                log.info("📢 [Subtitle Broadcasted] callId: {}, senderId: {}, subtitleId: {}, text: {}",
                        callId, realSenderId, savedSubtitle.getSubtitleId(), subtitleText);
            }
        } catch (Exception e) {
            log.error("🔴 [AI WebSocket] Failed to handle AI server message: {}", e.getMessage());
        }
    }
}