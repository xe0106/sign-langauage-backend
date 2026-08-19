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

    private static final String LANDMARK_FRAME = "landmark_frame";
    private static final String SESSION_END = "session_end";

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

    /** Relays the Android inference contract while retaining sender correlation in Spring only. */
    public void sendFeatures(AiFeatureMessage message) {
        if (message == null) {
            return;
        }

        String type = message.getType();
        if (!LANDMARK_FRAME.equals(type) && !SESSION_END.equals(type)) {
            sendClientError(message.getSenderId(), "INVALID_INFERENCE_MESSAGE",
                    "type must be landmark_frame or session_end", message.getSessionId(), message.getCallId());
            return;
        }
        if (message.getSessionId() == null || message.getCallId() == null || message.getTimestampMs() == null) {
            sendClientError(message.getSenderId(), "INVALID_INFERENCE_MESSAGE",
                    "sessionId, callId, and timestampMs are required", message.getSessionId(), message.getCallId());
            return;
        }
        if (LANDMARK_FRAME.equals(type)
                && (message.getSequence() == null || message.getFeatures() == null || message.getFeatures().size() != 258)) {
            sendClientError(message.getSenderId(), "INVALID_LANDMARK_FRAME",
                    "sequence and exactly 258 features are required", message.getSessionId(), message.getCallId());
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
            payload.put("type", type);
            payload.put("sessionId", message.getSessionId());
            payload.put("callId", message.getCallId());
            payload.put("timestampMs", message.getTimestampMs());
            if (LANDMARK_FRAME.equals(type)) {
                payload.put("sequence", message.getSequence());
                payload.put("features", message.getFeatures());
            }

            aiSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            log.info("[Spring -> AI] type: {}, seq: {}, callId: {}, sessionId: {}",
                    type, message.getSequence(), message.getCallId(), message.getSessionId());
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
                String sessionId = response.path("sessionId").asText();
                String callId = response.path("callId").asText();
                Long senderId = sessionSenderMap.get(sessionId);
                String code = response.path("code").asText("AI_ERROR");
                String errorMessage = response.path("message").asText("AI processing failed");
                log.error("[AI WebSocket] Error {}: {}", code, errorMessage);
                sendClientError(senderId, code, errorMessage, sessionId, callId);
                if (isTerminalAiError(code)) {
                    clearCompletedSession(sessionId);
                }
                return;
            }

            if ("status".equalsIgnoreCase(type)) {
                String status = response.path("status").asText();
                String sessionId = response.path("sessionId").asText();
                log.info("[AI Frame Status] status: {}, frames: {}/{}",
                        status,
                        response.path("bufferedFrames").asInt(),
                        response.path("requiredFrames").asInt());
                if ("completed_no_prediction".equals(status) || "model_unavailable".equals(status)) {
                    clearCompletedSession(sessionId);
                }
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
                    .sessionId(sessionId)
                    .senderId(senderId)
                    .textContent(label)
                    .subtitleId(savedSubtitle.getSubtitleId())
                    .createdAt(savedSubtitle.getCreatedAt())
                    .build();

            messagingTemplate.convertAndSend("/sub/call/" + callId, subtitleMessage);
            clearCompletedSession(sessionId);
            log.info("[Subtitle Broadcasted] callId: {}, senderId: {}, text: {}, confidence: {}",
                    callId, senderId, label, response.path("confidence").asDouble());
        } catch (Exception e) {
            log.error("[AI WebSocket] Failed to handle AI server message: {}", e.getMessage());
        }
    }

    private void clearCompletedSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionSenderMap.remove(sessionId);
        }
    }

    private boolean isTerminalAiError(String code) {
        return "UNKNOWN_SESSION".equals(code)
                || "INSUFFICIENT_SESSION_FRAMES".equals(code)
                || "INFERENCE_TIMEOUT".equals(code)
                || "INFERENCE_FAILED".equals(code);
    }

    private void sendClientError(Long senderId, String code, String message, String sessionId, String callId) {
        if (senderId == null) {
            return;
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "error");
        error.put("code", code);
        error.put("message", message);
        if (sessionId != null && !sessionId.isBlank()) {
            error.put("sessionId", sessionId);
        }
        if (callId != null && !callId.isBlank()) {
            error.put("callId", callId);
        }
        messagingTemplate.convertAndSend("/sub/errors/" + senderId, error);
    }
}
