package sign.language.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Android -> Spring -> AI 서버로 전달되는 258개 수어 특징 랜드마크 데이터 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFeatureMessage {
    @Builder.Default
    private String type = "landmark_frame"; // AI 서버 전송 표준 필드

    private String sessionId;      // 사용자별 UUID
    private String callId;         // 통화 UUID

    @JsonIgnore                    // AI 서버 릴레이 전송 시 JSON에서 제외 (AI 서버 extra="forbid" 호환)
    private Long senderId;         // 자막 송신자 ID

    private Long sequence;         // 프레임 시퀀스 (1부터 증가)
    private Long timestampMs;      // 타임스탬프 (ms)
    private List<Float> features;  // 258개의 Float 랜드마크 배열
}
