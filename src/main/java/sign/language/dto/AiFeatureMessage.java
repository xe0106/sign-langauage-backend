package sign.language.dto;

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
    private String sessionId;      // 사용자별 UUID
    private String callId;         // 통화 UUID
    private Long senderId;         // 자막 송신자 ID (선택)
    private Long sequence;         // 프레임 시퀀스 (1부터 증가)
    private Long timestampMs;      // 타임스탬프 (ms)
    private List<Float> features;  // 258개의 Float 랜드마크 배열
}
