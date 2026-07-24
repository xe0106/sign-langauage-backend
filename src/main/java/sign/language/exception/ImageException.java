package sign.language.exception;

import sign.language.errorcode.ErrorStatus;

public class ImageException extends GeneralException {
    public ImageException(ErrorStatus errorStatus) {
        super(errorStatus);
    }
}
