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
import org.springframework.web.socket.handler.TextWebSocketHandler;
import sign.language.dto.AiFeatureMessage;
import sign.language.dto.SignalMessage;

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spring -> AI 서버(ws://3.107.177.191:8000/ws/inference) 웹소켓 연동 클라이언트 서비스
 * 
 * 1. Android -> Spring으로 수신된 258개 MediaPipe 랜드마크 특징 데이터(features)를 AI 서버로 실시간 릴레이 전송
 * 2. AI 서버가 추론한 수어 자막 결과(prediction)를 수신 받아 통화방(/sub/call/{callId})으로 실시간 브로드캐스트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiWebSocketClientService extends TextWebSocketHandler {

    private static final String AI_WEBSOCKET_URI = "ws://3.107.177.191:8000/ws/inference";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

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
        this.aiSession = session;
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
     * Android -> Spring으로 수신된 특징 데이터를 AI 서버로 릴레이 전송
     */
    public void sendFeatures(AiFeatureMessage message) {
        if (aiSession == null || !aiSession.isOpen()) {
            log.warn("⚠️ [AI WebSocket] AI Session is not connected. Trying to reconnect...");
            connectToAiServer();
            return;
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(message);
            aiSession.sendMessage(new TextMessage(jsonPayload));
        } catch (IOException e) {
            log.error("🔴 [AI WebSocket] Failed to send features to AI server: {}", e.getMessage());
        }
    }

    /**
     * AI 서버로부터 수어 추론 결과(prediction)가 도착했을 때 처리
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            log.info("📩 [AI WebSocket Received] {}", payload);

            JsonNode rootNode = objectMapper.readTree(payload);

            String type = rootNode.has("type") ? rootNode.get("type").asText() : "";
            String status = rootNode.has("status") ? rootNode.get("status").asText() : "";
            String label = rootNode.has("label") ? rootNode.get("label").asText() : "";
            String prediction = rootNode.has("prediction") ? rootNode.get("prediction").asText() : "";
            String callId = rootNode.has("callId") ? rootNode.get("callId").asText() : "";
            Long senderId = rootNode.has("senderId") ? rootNode.get("senderId").asLong() : 1L;

            // 자막 텍스트 구하기 (label 우선, 없으면 prediction 사용)
            String subtitleText = (label != null && !label.trim().isEmpty()) ? label : prediction;

            // AI 추론 결과가 "prediction" 타입/상태이고 자막 텍스트가 존재할 때만 브로드캐스트
            if (("prediction".equalsIgnoreCase(type) || "prediction".equalsIgnoreCase(status))
                    && subtitleText != null && !subtitleText.trim().isEmpty()) {

                SignalMessage subtitleMessage = SignalMessage.builder()
                        .type(SignalMessage.MessageType.SUBTITLE)
                        .callId(callId)
                        .senderId(senderId)
                        .textContent(subtitleText)
                        .subtitleId(System.currentTimeMillis())
                        .createdAt(ZonedDateTime.now())
                        .build();

                // 통화방 구독자들(/sub/call/{callId})에게 실시간 자막 브로드캐스트
                messagingTemplate.convertAndSend("/sub/call/" + callId, subtitleMessage);
                log.info("📢 [Subtitle Broadcasted] callId: {}, text: {}", callId, subtitleText);
            }
        } catch (Exception e) {
            log.error("🔴 [AI WebSocket] Failed to handle AI server message: {}", e.getMessage());
        }
    }
}
