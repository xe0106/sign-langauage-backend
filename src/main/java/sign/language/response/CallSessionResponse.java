package sign.language.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sign.language.domain.CallSession;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 통화 세션 상태 응답 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSessionResponse {
    @Builder.Default
    private String type = "INCOMING_CALL"; // 알림 메시지 유형 (INCOMING_CALL 등)
    private String callId;      // 통화 세션 고유 ID (UUID)
    private Long callerId;      // 전화 건 사용자(발신자) ID
    private String callerName;  // 전화 건 사용자 이름
    private String callerNickname; // 전화 건 사용자 닉네임
    private String callerProfileImageUrl; // 전화 건 사용자 프로필 이미지 URL
    private Long receiverId;    // 전화 받는 사용자(수신자) ID
    private String status;      // 통화 상태 (RINGING, CONNECTED, REJECTED, ENDED)

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startedAt; // 통화 세션 생성/시작 시각

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endedAt;   // 통화 종료/거절 시각

    public static CallSessionResponse from(CallSession session) {
        return CallSessionResponse.builder()
                .callId(session.getCallId())
                .callerId(session.getCaller() != null ? session.getCaller().getId() : null)
                .callerName(session.getCaller() != null ? session.getCaller().getName() : null)
                .callerNickname(session.getCaller() != null ? session.getCaller().getNickname() : null)
                .callerProfileImageUrl(session.getCaller() != null ? session.getCaller().getProfileImageUrl() : null)
                .receiverId(session.getReceiver() != null ? session.getReceiver().getId() : null)
                .status(session.getStatus() != null ? session.getStatus().name() : null)
                .startedAt(toLocalDateTime(session.getStartedAt()))
                .endedAt(toLocalDateTime(session.getEndedAt()))
                .build();
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }
}
