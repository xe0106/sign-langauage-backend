package sign.language.exception;

import sign.language.errorcode.ErrorStatus;

public class ContactException extends GeneralException {
    public ContactException(ErrorStatus errorStatus) {
        super(errorStatus);
    }
}
