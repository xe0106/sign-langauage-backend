package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 통화 세션 상태 응답 DTO
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class CallSessionResponse {
    private String callId;      // 통화 세션 고유 ID (UUID)
    private Long callerId;      // 전화 건 사용자(발신자) ID
    private Long receiverId;    // 전화 받는 사용자(수신자) ID
    private String status;      // 통화 상태 (RINGING, CONNECTED, REJECTED, ENDED)
    private LocalDateTime startedAt; // 통화 세션 생성/시작 시각
    private LocalDateTime endedAt;   // 통화 종료/거절 시각
}
