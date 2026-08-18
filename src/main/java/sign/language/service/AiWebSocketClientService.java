package sign.language.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String AI_WEBSOCKET_URI = "ws://3.107.177.191:8000/ws/inference";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final CallService callService;

    private WebSocketSession aiSession;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();

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
            webSocketClient.doHandshake(this, new WebSocketHttpHeaders(), URI.create(AI_WEBSOCKET_URI));
            log.info("🟢 [AI WebSocket] Connecting to AI Server: {}", AI_WEBSOCKET_URI);
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
     * AI 서버 규격에 맞춰 필드를 정제한 후 전송
     */
    public void sendFeatures(AiFeatureMessage message) {
        if (aiSession == null || !aiSession.isOpen()) {
            log.warn("⚠️ [AI WebSocket] AI Session is not connected. Trying to reconnect...");
            connectToAiServer();
            return;
        }

        try {
            // 규격서에 명시된 정확한 6개 필드만 JSON으로 구성
            java.util.Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
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

            // 1. 상태 알림 (warming_up, analyzing)
            if ("status".equalsIgnoreCase(type)) {
                String status = rootNode.path("status").asText();
                int buffered = rootNode.path("bufferedFrames").asInt();
                int required = rootNode.path("requiredFrames").asInt();
                log.info("ℹ️ [AI Frame Status] status: {}, frames: {}/{}", status, buffered, required);
                return;
            }

            // 2. 최종 추론 결과 (prediction)
            if ("prediction".equalsIgnoreCase(type)) {
                String callId = rootNode.path("callId").asText();
                String label = rootNode.path("label").asText();
                double confidence = rootNode.path("confidence").asDouble();

                if (label != null && !label.trim().isEmpty() && !callId.isBlank()) {
                    // 통화방 세션 조회 등을 통해 senderId가 필요한 경우 기본값(예: 1L) 또는 조회값 사용
                    Long senderId = 1L;

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
                    log.info("📢 [Subtitle Broadcasted] callId: {}, text: {}, confidence: {}", callId, label, confidence);
                }
            }
        } catch (Exception e) {
            log.error("🔴 [AI WebSocket] Failed to handle AI server message: {}", e.getMessage());
        }
    }
}