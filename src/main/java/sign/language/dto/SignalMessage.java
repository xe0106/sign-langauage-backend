package sign.language.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

/**
 * WebRTC 시그널링 및 실시간 자막 전송 메시지 객체 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalMessage {

    /**
     * 시그널링 메시지 유형 구분
     */
    public enum MessageType {
        JOIN,          // 통화방 최초 입장 알림
        OFFER,         // WebRTC 미디어 SDP Offer 신호
        ANSWER,        // WebRTC 미디어 SDP Answer 응답 신호
        ICE_CANDIDATE, // WebRTC 네트워크 경로 탐색(ICE Candidate) 신호
        SUBTITLE,      // 실시간 수어 번역 자막 텍스트
        LEAVE          // 통화 종료 및 방 퇴장 알림
    }

    private MessageType type;    // 메시지 종류 (JOIN, OFFER, ANSWER, ICE_CANDIDATE, SUBTITLE, LEAVE)
    private String callId;       // 통화 방 고유 ID
    private Long senderId;       // 메시지를 보낸 사용자 ID
    private Long receiverId;     // 메시지를 받는 대상 사용자 ID
    private Object data;         // SDP 객체 데이터 또는 ICE Candidate 객체 데이터
    private String textContent;  // 실시간 자막 내용 (type가 SUBTITLE일 때 사용)

    private Long subtitleId;     // 자막 고유 ID (type가 SUBTITLE일 때 서버에서 부여)

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime createdAt; // 실시간 자막 생성/기록 시각 (ISO-8601 형식)
}
