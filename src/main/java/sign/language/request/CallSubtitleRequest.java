package sign.language.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 자막 전송 및 저장 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CallSubtitleRequest {
    private Long senderId;      // 자막을 송신하는 사용자 ID
    private String textContent; // 번역된 수어 자막 텍스트 내용
}
