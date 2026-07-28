package sign.language.exception;

import sign.language.errorcode.ErrorStatus;

public class UserProfileException extends GeneralException {
    public UserProfileException(ErrorStatus errorStatus) {
        super(errorStatus);
    }
}
