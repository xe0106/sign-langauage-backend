package sign.language.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiFeatureMessage {

    @Builder.Default
    private String type = "landmark_frame"; // AI 서버 필수 필드

    private String sessionId;               // 사용자별 UUID
    private String callId;                  // 통화 UUID
    private Long senderId;                  // Spring 내부 관리용 (AI 전송 시에는 제외됨)
    private Long sequence;                  // 프레임 시퀀스
    private Long timestampMs;               // ms 타임스탬프
    private List<Float> features;           // 258개 float 배열
}