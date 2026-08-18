package sign.language.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    // 1. type 필드는 수신용 (AI 서버 전송 시 null 처리하여 제외)
    private String type;

    // 2. 클라이언트 수신: sessionId 또는 session_id 둘 다 수용 / AI 서버 전송: session_id로 직렬화
    @JsonProperty("session_id")
    @JsonAlias({"sessionId", "session_id"})
    private String sessionId;

    @JsonProperty("call_id")
    @JsonAlias({"callId", "call_id"})
    private String callId;

    @JsonProperty("sender_id")
    @JsonAlias({"senderId", "sender_id"})
    private Long senderId;

    @JsonProperty("sequence")
    @JsonAlias({"sequence"})
    private Long sequence;

    @JsonProperty("timestamp_ms")
    @JsonAlias({"timestampMs", "timestamp_ms"})
    private Long timestampMs;

    @JsonProperty("features")
    @JsonAlias({"features"})
    private List<Float> features;
}