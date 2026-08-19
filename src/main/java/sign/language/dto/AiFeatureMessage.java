package sign.language.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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

    @Builder.Default
    private String type = "landmark_frame"; // landmark_frame 또는 session_end

    private String sessionId;      // 사용자별 UUID
    private String callId;         // 통화 UUID
    private Long senderId;         // 자막 송신자 ID (Android -> Spring 수신용, AI 전송 시 payloadMap에서 제외됨)
    private Long sequence;         // landmark_frame에서만 사용: 0부터 증가
    private Long timestampMs;      // Unix epoch millisecond
    private List<Float> features;  // landmark_frame에서만 사용: 258개 Float 배열
}
