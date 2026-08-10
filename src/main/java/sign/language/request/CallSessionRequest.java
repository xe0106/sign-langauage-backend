package sign.language.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 통화 세션 상태 변경 요청 DTO
 */
@Getter
@Setter
public class CallSessionRequest {
    private String status; // 변경할 통화 상태 (RINGING, CONNECTED, REJECTED, ENDED 중 하나)
}
