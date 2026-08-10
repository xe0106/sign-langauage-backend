package sign.language.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 통화 세션 생성(전화 걸기) 요청 DTO
 */
@Getter
@Setter
public class CallCreateRequest {
    private Long callerId;   // 전화 걸 사용자(발신자) ID
    private Long receiverId; // 전화 받을 사용자(수신자) ID
}
