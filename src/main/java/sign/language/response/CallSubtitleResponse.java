package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 자막 정보 응답 DTO
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class CallSubtitleResponse {
    private Long subtitleId;    // 자막 고유 ID
    private String callId;      // 통화 세션 고유 ID
    private Long senderId;      // 자막 송신자 ID
    private String textContent; // 번역된 수어 텍스트 내용
    private LocalDateTime createdAt; // 자막 생성/기록 시각
}
