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
import org.springframework.web.socket.handler.TextWebSocketHandler;
import sign.language.domain.CallSession;
import sign.language.domain.CallSubtitle;
import sign.language.domain.User;
import sign.language.dto.AiFeatureMessage;
import sign.language.dto.SignalMessage;
import sign.language.repository.CallRepository;
import sign.language.repository.SubtitleRepository;
import sign.language.repository.UserRepository;

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spring -> AI 서버(ws://3.107.177.191:8000/ws/inference) 웹소켓 연동 클라이언트 서비스
 * 
 * 1. Android -> Spring으로 수신된 258개 MediaPipe 랜드마크 특징 데이터(features)를 AI 서버로 실시간 릴레이 전송
 * 2. AI 서버가 추론한 수어 자막 결과(prediction)를 수신 받아 DB에 저장하고 통화방(/sub/call/{callId})으로 실시간 브로드캐스트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiWebSocketClientService extends TextWebSocketHandler {

    @Value("${ai.websocket.url:ws://3.107.177.191:8000/ws/inference}")
    private String aiWebsocketUri;

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final CallRepository callRepository;
    private final UserRepository userRepository;
    private final SubtitleRepository subtitleRepository;

    private WebSocketSession aiSession;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();

    // 통화방(callId)별 최근 랜드마크를 보낸 발신자 ID(senderId) 매핑 보존 (senderId 1L 고정 방지)
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
        if (message == null) return;

        // callId별 발신자 ID 보존 (추후 AI 추론 자막 생성 시 진짜 senderId 복원)
        if (message.getSenderId() != null) {
            lastSenderIdMap.put(message.getCallId(), message.getSenderId());
        }

        if (aiSession != null && aiSession.isOpen()) {
            try {
                String jsonPayload = objectMapper.writeValueAsString(message);
                aiSession.sendMessage(new TextMessage(jsonPayload));
                log.info("📤 [AI WebSocket Relay Sent] callId: {}, seq: {}", message.getCallId(), message.getSequence());
            } catch (IOException e) {
                log.error("🔴 [AI WebSocket] Failed to send message to AI Server: {}", e.getMessage());
            }
        } else {
            log.warn("⚠️ [AI WebSocket] AI session is not connected. Attempting reconnection...");
            connectToAiServer();
        }
    }

    /**
     * AI 서버로부터 수어 추론 결과(prediction) 또는 에러(error)가 도착했을 때 처리
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

            // 1. AI 서버 에러 응답 수신 처리 -> 클라이언트 통화방으로 알림 전달
            if ("error".equalsIgnoreCase(type)) {
                String errorCode = rootNode.has("code") ? rootNode.get("code").asText() : "AI_ERROR";
                String errorMsg = rootNode.has("message") ? rootNode.get("message").asText() : "AI processing failed";
                log.error("🔴 [AI WebSocket Error] code: {}, message: {}", errorCode, errorMsg);

                if (callId != null && !callId.isEmpty()) {
                    SignalMessage errorMessage = SignalMessage.builder()
                            .type(SignalMessage.MessageType.SUBTITLE)
                            .callId(callId)
                            .senderId(lastSenderIdMap.getOrDefault(callId, 1L))
                            .textContent("[AI ERROR: " + errorCode + "] " + errorMsg)
                            .subtitleId(System.currentTimeMillis())
                            .createdAt(ZonedDateTime.now())
                            .build();
                    messagingTemplate.convertAndSend("/sub/call/" + callId, errorMessage);
                }
                return;
            }

            // 2. AI 서버 상태 알림 (warming_up, analyzing 등)
            if ("status".equalsIgnoreCase(type)) {
                int buffered = rootNode.has("bufferedFrames") ? rootNode.get("bufferedFrames").asInt() : 0;
                int required = rootNode.has("requiredFrames") ? rootNode.get("requiredFrames").asInt() : 30;
                log.info("ℹ️ [AI Frame Status] status: {}, frames: {}/{}", status, buffered, required);
                return;
            }

            // 3. AI 추론 결과 처리 (DB 저장 후 senderId 동적 복원하여 브로드캐스트)
            String subtitleText = (label != null && !label.trim().isEmpty()) ? label : prediction;
            Long realSenderId = rootNode.has("senderId") ? rootNode.get("senderId").asLong() : lastSenderIdMap.getOrDefault(callId, 1L);

            if (("prediction".equalsIgnoreCase(type) || "prediction".equalsIgnoreCase(status))
                    && subtitleText != null && !subtitleText.trim().isEmpty()) {

                // DB 영속 저장 수행
                SignalMessage subtitleMessage = saveAndBuildSubtitleMessage(callId, realSenderId, subtitleText);

                // 통화방 구독자들(/sub/call/{callId})에게 실시간 자막 브로드캐스트
                messagingTemplate.convertAndSend("/sub/call/" + callId, subtitleMessage);
                log.info("📢 [Subtitle Broadcasted] callId: {}, senderId: {}, text: {}", callId, realSenderId, subtitleText);
            }
        } catch (Exception e) {
            log.error("🔴 [AI WebSocket] Failed to handle AI server message: {}", e.getMessage());
        }
    }

    /**
     * AI 수어 자막 결과를 DB에 영구 저장하고 브로드캐스트용 SignalMessage 생성
     */
    private SignalMessage saveAndBuildSubtitleMessage(String callId, Long senderId, String textContent) {
        Long subtitleId = System.currentTimeMillis();
        ZonedDateTime createdAt = ZonedDateTime.now();

        try {
            Optional<CallSession> callSessionOpt = callRepository.findById(callId);
            Optional<User> senderOpt = userRepository.findById(senderId);

            if (callSessionOpt.isPresent() && senderOpt.isPresent()) {
                CallSubtitle subtitle = CallSubtitle.create(callSessionOpt.get(), senderOpt.get(), textContent);
                CallSubtitle savedSubtitle = subtitleRepository.save(subtitle);
                subtitleId = savedSubtitle.getId();
                if (savedSubtitle.getCreatedAt() != null) {
                    createdAt = savedSubtitle.getCreatedAt().atZone(java.time.ZoneId.systemDefault());
                }
                log.info("💾 [AI Subtitle Saved DB] subtitleId: {}, callId: {}", subtitleId, callId);
            } else {
                log.warn("⚠️ [AI Subtitle DB Skip] Session or User not found in DB. Broadcasting with transient ID.");
            }
        } catch (Exception e) {
            log.error("🔴 [AI Subtitle Save Error] Failed to persist to DB: {}", e.getMessage());
        }

        return SignalMessage.builder()
                .type(SignalMessage.MessageType.SUBTITLE)
                .callId(callId)
                .senderId(senderId)
                .textContent(textContent)
                .subtitleId(subtitleId)
                .createdAt(createdAt)
                .build();
    }
}
