package sign.language.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import sign.language.dto.AiFeatureMessage;
import sign.language.dto.SignalMessage;
import sign.language.request.CallSubtitleRequest;
import sign.language.response.CallSubtitleResponse;
import sign.language.service.AiWebSocketClientService;
import sign.language.service.CallService;

import java.util.Map;

/**
 * WebRTC 시그널링 및 실시간 자막 전송 컨트롤러
 *
 * WebSocket(STOMP)을 통해 클라이언트 간의 미디어 신호(Offer, Answer, ICE Candidate)
 * 및 실시간 수어 번역 자막(Subtitle)을 중계(Relay)합니다.
 */
@Controller
@RequiredArgsConstructor
public class SignalingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final AiWebSocketClientService aiWebSocketClientService;
    private final CallService callService;

    /**
     * WebRTC 시그널링 신호 및 실시간 자막 릴레이 메소드
     *
     * @param message 클라이언트가 발송한 시그널링 메시지 객체
     *
     * [클라이언트 통신 규칙]
     * - 메시지 송신(Publish) 경로: /pub/call/signal
     * - 메시지 수신(Subscribe) 경로: /sub/call/{callId}
     */
    @MessageMapping("/call/signal")
    public void handleSignal(SignalMessage message) {

        // 1. 자막(SUBTITLE) 메시지 처리
        if (SignalMessage.MessageType.SUBTITLE.equals(message.getType())) {
            try {
                CallSubtitleRequest subtitleRequest = new CallSubtitleRequest(
                        message.getSenderId(),
                        message.getTextContent()
                );

                // DB 저장 및 상태 검증 우선 수행
                CallSubtitleResponse savedResponse = callService.saveSubtitle(message.getCallId(), subtitleRequest);

                // DB에서 생성된 subtitleId와 createdAt을 메시지에 추가
                message.setSubtitleId(savedResponse.getSubtitleId());
                message.setCreatedAt(savedResponse.getCreatedAt());

            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "자막 저장 중 오류가 발생했습니다.";

                // DB 저장 실패 시 구독 채널로 전송하지 않고 송신자에게만 실패 전달
                messagingTemplate.convertAndSend(
                        "/sub/errors/" + message.getSenderId(),
                        Map.of(
                                "status", "ERROR",
                                "code", "SUBTITLE_SAVE_FAILED",
                                "message", errorMsg
                        )
                );
                return; // 브로드캐스트 타지 않고 종료
            }
        }

        // 2. [저장 성공한 자막] 또는 [WebRTC 시그널링 신호(Offer/Answer/ICE)] 브로드캐스트
        messagingTemplate.convertAndSend("/sub/call/" + message.getCallId(), message);
    }

    /**
     * Android -> Spring: 258개 MediaPipe 랜드마크 특징 데이터(features) 수신 메소드
     *
     * [클라이언트 통신 규칙]
     * - 메시지 송신(Publish) 경로: /pub/ai/features
     */
    @MessageMapping("/ai/features")
    public void handleAiFeatures(AiFeatureMessage message) {
        // 수신된 258개 랜드마크 특징 데이터를 AI 웹소켓 서버로 릴레이 전송
        aiWebSocketClientService.sendFeatures(message);
    }
}