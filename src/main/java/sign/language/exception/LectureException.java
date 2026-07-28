package sign.language.exception;

import sign.language.errorcode.ErrorStatus;

public class LectureException extends GeneralException {
    public LectureException(ErrorStatus errorStatus) {
        super(errorStatus);
    }
}
