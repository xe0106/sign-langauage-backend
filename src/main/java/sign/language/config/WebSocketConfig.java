package sign.language.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 및 STOMP 메세지 브로커 환경 설정 클래스
 * 
 * WebRTC 시그널링 통신 및 실시간 수어 번역 자막 브로커 개설을 담당합니다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * WebSocket 핸드셰이크 접속 엔드포인트 등록
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS 연결 지원 엔드포인트 (/ws-stomp)
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // 순수 WebSocket 연결 지원 엔드포인트 (/ws-stomp)
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*");
    }

    /**
     * 메시지 브로커 구독/발행 경로 규칙 설정
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /sub 접두사가 붙은 경로: 메시지 구독(Subscribe)용 경로 (서버 -> 클라이언트 전송)
        registry.enableSimpleBroker("/sub");

        // /pub 접두사가 붙은 경로: 메시지 발행(Publish)용 경로 (클라이언트 -> 서버 전송)
        registry.setApplicationDestinationPrefixes("/pub");
    }
}
