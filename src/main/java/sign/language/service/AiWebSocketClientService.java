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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spring -> AI server WebSocket relay client.
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

    // Prediction responses include sessionId, so this remains correct for concurrent callers.
    private final Map<String, Long> sessionSenderMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        connectToAiServer();
    }

    public synchronized void connectToAiServer() {
        if (aiSession != null && aiSession.isOpen()) {
            return;
        }

        try {
            StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
            webSocketClient.doHandshake(this, new WebSocketHttpHeaders(), URI.create(aiWebsocketUri));
            log.info("[AI WebSocket] Connecting to AI server: {}", aiWebsocketUri);
        } catch (Exception e) {
            log.error("[AI WebSocket] Failed to connect to AI server: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.aiSession = new ConcurrentWebSocketSessionDecorator(session, 5000, 512 * 1024);
        log.info("[AI WebSocket] Connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("[AI WebSocket] Connection closed: {}. Scheduling reconnect.", status);
        this.aiSession = null;
        scheduleReconnect();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[AI WebSocket] Transport error: {}", exception.getMessage());
    }

    private void scheduleReconnect() {
        reconnectScheduler.schedule(this::connectToAiServer, 5, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        return aiSession != null && aiSession.isOpen();
    }

    /**
     * Relays only the six fields accepted by the AI LandmarkFrame contract.
     */
    public void sendFeatures(AiFeatureMessage message) {
        if (message == null) {
            return;
        }

        if (message.getSessionId() != null && message.getSenderId() != null) {
            sessionSenderMap.put(message.getSessionId(), message.getSenderId());
        }

        if (!isConnected()) {
            log.warn("[AI WebSocket] AI session is not connected. Attempting reconnection.");
            connectToAiServer();
            return;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "landmark_frame");
            payload.put("sessionId", message.getSessionId());
            payload.put("callId", message.getCallId());
            payload.put("sequence", message.getSequence());
            payload.put("timestampMs", message.getTimestampMs());
            payload.put("features", message.getFeatures());

            aiSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            log.info("[Spring -> AI] seq: {}, callId: {}", message.getSequence(), message.getCallId());
        } catch (IOException e) {
            log.error("[AI WebSocket] Failed to send features: {}", e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode response = objectMapper.readTree(message.getPayload());
            String type = response.path("type").asText();

            if ("error".equalsIgnoreCase(type)) {
                // Current AI error payloads do not include callId or sessionId, so do not misroute them.
                log.error("[AI WebSocket] Error {}: {}",
                        response.path("code").asText("AI_ERROR"),
                        response.path("message").asText("AI processing failed"));
                return;
            }

            if ("status".equalsIgnoreCase(type)) {
                log.info("[AI Frame Status] status: {}, frames: {}/{}",
                        response.path("status").asText(),
                        response.path("bufferedFrames").asInt(),
                        response.path("requiredFrames").asInt());
                return;
            }

            if (!"prediction".equalsIgnoreCase(type)) {
                return;
            }

            String callId = response.path("callId").asText();
            String sessionId = response.path("sessionId").asText();
            String label = response.path("label").asText();
            Long senderId = sessionSenderMap.get(sessionId);

            if (callId.isBlank() || label.isBlank() || senderId == null) {
                log.warn("[AI Subtitle Skip] Missing callId, label, or sender mapping for sessionId: {}", sessionId);
                return;
            }

            CallSubtitleResponse savedSubtitle = callService.saveSubtitle(
                    callId, new CallSubtitleRequest(senderId, label)
            );
            SignalMessage subtitleMessage = SignalMessage.builder()
                    .type(SignalMessage.MessageType.SUBTITLE)
                    .callId(callId)
                    .senderId(senderId)
                    .textContent(label)
                    .subtitleId(savedSubtitle.getSubtitleId())
                    .createdAt(savedSubtitle.getCreatedAt())
                    .build();

            messagingTemplate.convertAndSend("/sub/call/" + callId, subtitleMessage);
            sessionSenderMap.remove(sessionId);
            log.info("[Subtitle Broadcasted] callId: {}, senderId: {}, text: {}, confidence: {}",
                    callId, senderId, label, response.path("confidence").asDouble());
        } catch (Exception e) {
            log.error("[AI WebSocket] Failed to handle AI server message: {}", e.getMessage());
        }
    }
}
