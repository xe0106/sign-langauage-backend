package sign.language.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus {

    // 공통
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 오류가 발생했습니다."),

    // 매개변수 누락
    EMPTY_PARAMETER(HttpStatus.BAD_REQUEST, "PARAM400", "필수 요청 매개변수가 누락되었습니다."),
    PAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "요청하신 URL 경로를 찾을 수 없습니다."),

    // 회원, 프로필
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404", "존재하지 않는 회원입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "MEMBER409_EMAIL", "이미 가입된 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "MEMBER409_NICKNAME", "이미 사용 중인 닉네임입니다."),
    DUPLICATE_PHONE_NUMBER(HttpStatus.CONFLICT, "MEMBER409_PHONE_NUMBER", "이미 사용 중인 전화번호입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "MEMBER401", "비밀번호가 일치하지 않습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "MEMBER400_FORMAT", "입력값 형식이 올바르지 않습니다."),
    LOGOUT_FAILED(HttpStatus.BAD_REQUEST, "MEMBER400_LOGOUT", "로그아웃 처리에 실패했습니다."),
    DELETED_MEMBER(HttpStatus.NOT_FOUND, "MEMBER404_DELETED", "탈퇴한 회원입니다."),

    // 파일 / S3 관련 에러 코드
    INVALID_FILE(HttpStatus.NOT_FOUND, "FILE404", "유효하지 않거나 비어있는 파일입니다."),
    INVALID_FILE_EXTENSION(HttpStatus.NOT_FOUND, "FILE400_EXTENSION", "잘못된 파일 확장자명 입니다."),
    FILE_UPLOAD_FAILED(HttpStatus.BAD_REQUEST, "FILE400_DEFAULT", "S3 이미지 업로드에 실패했습니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "FILE400_MAX", "파일 용량이 허용 범위를 초과했습니다."),

    // 수어 강의 관련 에러 코드
    LECTURE_NOT_FOUND(HttpStatus.NOT_FOUND, "LECTURE404", "존재하지 않는 강의 ID입니다."),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "LECTURE400", "지원하지 않는 카테고리 형식입니다."),

    // 퀴즈 관련 에러 코드
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "QUIZ404", "퀴즈가 존재하지 않습니다."),
    QUIZ_INVALID_COUNT(HttpStatus.BAD_REQUEST, "QUIZ400", "퀴즈 요청 개수는 1개 이상 20개 이하여야 합니다."),

    // 연락처 관련 에러 코드
    CANNOT_ADD_SELF(HttpStatus.BAD_REQUEST, "CONTACT400", "자기 자신을 추가할 수 없습니다."),
    CONTACT_ALREADY_EXISTS(HttpStatus.CONFLICT, "CONTACT409", "이미 등록된 연락처입니다."),
    CONTACT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTACT404", "연락처가 존재하지 않습니다."),

    // 송신 및 수신 에러 코드
    CALLER_NOT_FOUND(HttpStatus.NOT_FOUND, "CALLER404", "발신자가 존재하지 않습니다."),
    RECEIVER_NOT_FOUND(HttpStatus.NOT_FOUND, "RECEIVER404", "수신자가 존재하지 않습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION404", "통화 세션이 존재하지 않습니다."),
    INVALID_CALL_STATUS(HttpStatus.BAD_REQUEST, "STATUS400", "세션 이름이 유효하지 않습니다.")
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}