package sign.language.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import sign.language.domain.CallSubtitle;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime createdAt; // 자막 생성/기록 시각 (ISO-8601 타임존 포함)

    // Entity -> Response DTO 변환 정적 팩토리 메서드
    public static CallSubtitleResponse from(CallSubtitle subtitle) {
        return CallSubtitleResponse.builder()
                .subtitleId(subtitle.getId())
                .callId(subtitle.getCall().getCallId())
                .senderId(subtitle.getSender().getId())
                .textContent(subtitle.getTextContent())
                .createdAt(toZonedDateTime(subtitle.getCreatedAt()))
                .build();
    }

    private static ZonedDateTime toZonedDateTime(Instant instant) {
        return instant != null ? instant.atZone(ZoneId.of("Asia/Seoul")) : null;
    }
}