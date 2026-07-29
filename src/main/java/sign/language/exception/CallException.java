package sign.language.exception;

import sign.language.errorcode.ErrorStatus;

public class CallException extends GeneralException {
    public CallException(ErrorStatus errorStatus) {
        super(errorStatus);
    }
}
