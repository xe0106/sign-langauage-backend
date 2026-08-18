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
 * Spring -> AI 서버 웹소켓 연동 클라이언트 서비스
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

    // 통화방(callId)별 최근 랜드마크를 보낸 발신자 ID(senderId) 매핑 (1L 하드코딩 방지)
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

    private void scheduleReconnect() {
        reconnectScheduler.schedule(this::connectToAiServer, 5, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        return aiSession != null && aiSession.isOpen();
    }

    /**
     * AI 서버 규격에 맞춰 필드를 정제한 후 전송
     */
    public void sendFeatures(AiFeatureMessage message) {
        if (message == null) return;

        if (aiSession == null || !aiSession.isOpen()) {
            log.warn("⚠️ [AI WebSocket] AI Session is not connected. Trying to reconnect...");
            connectToAiServer();
            return;
        }

        try {
            // 발신자 ID 보존 (AI 추론 자막 수신 시 senderId 복원용)
            if (message.getSenderId() != null && message.getCallId() != null) {
                lastSenderIdMap.put(message.getCallId(), message.getSenderId());
            }

            // 규격서에 명시된 정확한 6개 필드만 JSON으로 구성
            Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
            payloadMap.put("type", "landmark_frame");
            payloadMap.put("sessionId", message.getSessionId());
            payloadMap.put("callId", message.getCallId());
            payloadMap.put("sequence", message.getSequence());
            payloadMap.put("timestampMs", message.getTimestampMs());
            payloadMap.put("features", message.getFeatures());

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            log.info("📤 [Spring -> AI Payload] seq: {}, callId: {}", message.getSequence(), message.getCallId());

            aiSession.sendMessage(new TextMessage(jsonPayload));
        } catch (IOException e) {
            log.error("🔴 [AI WebSocket] Failed to send features to AI server: {}", e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            log.info("📩 [AI WebSocket Received] {}", payload);

            JsonNode rootNode = objectMapper.readTree(payload);
            String type = rootNode.has("type") ? rootNode.get("type").asText() : "";

            // 1. 에러 응답 수신
            if ("error".equalsIgnoreCase(type)) {
                String errorCode = rootNode.path("code").asText("AI_ERROR");
                String errorMsg = rootNode.path("message").asText("AI processing failed");
                log.error("🔴 [AI WebSocket Error] code: {}, message: {}", errorCode, errorMsg);
                return;
            }

            // 2. 상태 알림 (warming_up, analyzing)
            if ("status".equalsIgnoreCase(type)) {
                String status = rootNode.path("status").asText();
                int buffered = rootNode.path("bufferedFrames").asInt();
                int required = rootNode.path("requiredFrames").asInt();
                log.info("ℹ️ [AI Frame Status] status: {}, frames: {}/{}", status, buffered, required);
                return;
            }

            // 3. 최종 추론 결과 (prediction)
            if ("prediction".equalsIgnoreCase(type)) {
                String callId = rootNode.path("callId").asText();
                String label = rootNode.path("label").asText();
                double confidence = rootNode.path("confidence").asDouble();

                if (label != null && !label.trim().isEmpty() && !callId.isBlank()) {
                    // 저장해둔 실제 발화자 senderId 복원 (기본값 1L)
                    Long senderId = lastSenderIdMap.getOrDefault(callId, 1L);

                    CallSubtitleRequest subtitleRequest = new CallSubtitleRequest(senderId, label);
                    CallSubtitleResponse savedSubtitle = callService.saveSubtitle(callId, subtitleRequest);

                    SignalMessage subtitleMessage = SignalMessage.builder()
                            .type(SignalMessage.MessageType.SUBTITLE)
                            .callId(callId)
                            .senderId(senderId)
                            .textContent(label)
                            .subtitleId(savedSubtitle.getSubtitleId())
                            .createdAt(savedSubtitle.getCreatedAt())
                            .build();

                    messagingTemplate.convertAndSend("/sub/call/" + callId, subtitleMessage);
                    log.info("📢 [Subtitle Broadcasted] callId: {}, senderId: {}, text: {}, confidence: {}",
                            callId, senderId, label, confidence);
                }
            }
        } catch (Exception e) {
            log.error("🔴 [AI WebSocket] Failed to handle AI server message: {}", e.getMessage());
        }
    }
}