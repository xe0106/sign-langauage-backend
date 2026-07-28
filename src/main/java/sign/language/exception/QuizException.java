package sign.language.exception;

import sign.language.errorcode.ErrorStatus;

public class QuizException extends GeneralException {
    public QuizException(ErrorStatus errorStatus) {
        super(errorStatus);
    }
}
