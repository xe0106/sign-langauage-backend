package sign.language.response;

public class SignResponse {
    private boolean success;
    private String message;
    private String userName;
    private String token;

    // 로그인 성공
    public SignResponse(boolean success, String message, String userName, String token) {
        this.success = success;
        this.message = message;
        this.userName = userName;
        this.token = token;
    }

    // 회원가입 성공 / 로그인 실패
    public SignResponse(boolean success, String message, String userName) {
        this.success = success;
        this.message = message;
        this.userName = userName;
        this.token = null;
    }

    // 회원가입 실패
    public SignResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.userName = null;
        this.token = null;
    }

    // Getter 메서드들 (기존과 동일)
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getUserName() { return userName; }
    public String getToken() { return token; }
}