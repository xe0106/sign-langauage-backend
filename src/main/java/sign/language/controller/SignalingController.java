package sign.language.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import sign.language.dto.SignalMessage;

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
        // 해당 callId 통화 방을 구독 중인 수신자(들)에게 메시지를 실시간으로 브로드캐스트 전송
        messagingTemplate.convertAndSend("/sub/call/" + message.getCallId(), message);
    }
}
