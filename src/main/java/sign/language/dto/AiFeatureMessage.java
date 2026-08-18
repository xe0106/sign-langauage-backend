package sign.language.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiFeatureMessage {

    // 1. type 필드는 Android/웹에서 들어올 땐 받고, AI 서버로 나갈 땐 null이면 JSON에서 제외됨
    private String type;

    // 2. AI 서버(FastAPI)가 요구하는 snake_case 매핑
    @JsonProperty("session_id")
    private String sessionId;      // 사용자별 UUID

    @JsonProperty("call_id")
    private String callId;         // 통화 UUID

    @JsonProperty("sender_id")
    private Long senderId;         // 자막 송신자 ID

    private Long sequence;         // 프레임 시퀀스 (1부터 증가)

    @JsonProperty("timestamp_ms")
    private Long timestampMs;      // 타임스탬프 (ms)

    private List<Float> features;  // 258개의 Float 랜드마크 배열
}