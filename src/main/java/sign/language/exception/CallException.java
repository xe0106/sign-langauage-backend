package sign.language.exception;

import sign.language.errorcode.ErrorStatus;

/**
 * 화상 통화 전용 커스텀 예외 클래스
 * 
 * 통화 세션 없음, 이미 종료된 통화 등의 상황에서 예외를 던질 때 사용합니다.
 */
public class CallException extends GeneralException {
    public CallException(ErrorStatus errorStatus) {
        super(errorStatus);
    }
}
