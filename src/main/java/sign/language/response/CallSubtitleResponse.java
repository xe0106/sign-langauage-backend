package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import sign.language.domain.CallSubtitle;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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

    // ⭐️ Entity -> Response DTO 변환 정적 팩토리 메서드
    public static CallSubtitleResponse from(CallSubtitle subtitle) {
        return CallSubtitleResponse.builder()
                .subtitleId(subtitle.getId())
                .callId(subtitle.getCall().getCallId())
                .senderId(subtitle.getSender().getId())
                .textContent(subtitle.getTextContent())
                .createdAt(toLocalDateTime(subtitle.getCreatedAt()))
                .build();
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }
}
