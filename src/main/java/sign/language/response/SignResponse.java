package sign.language.response;

import lombok.Getter;

@Getter
public class SignResponse {
    private boolean success;
    private String message;
    private String userName;
    private String token;
    private Boolean available; // 닉네임 중복확인용

    // 로그인 성공 시
    public SignResponse(boolean success, String message, String userName, String token) {
        this.success = success;
        this.message = message;
        this.userName = userName;
        this.token = token;
    }

    // 회원가입 성공 시
    public SignResponse(boolean success, String message, String userName) {
        this.success = success;
        this.message = message;
        this.userName = userName;
    }

    // 실패 / 공통 응답용
    public SignResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // 닉네임 중복확인 응답용
    public SignResponse(boolean success, String message, boolean available) {
        this.success = success;
        this.message = message;
        this.available = available;
    }
}