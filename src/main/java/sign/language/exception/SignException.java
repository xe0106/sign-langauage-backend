package sign.language.exception;

import sign.language.errorcode.ErrorStatus;

public class SignException extends GeneralException {
    public SignException(ErrorStatus errorStatus) {
        super(errorStatus);
    }
}
