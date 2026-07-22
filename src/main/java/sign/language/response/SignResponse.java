package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class SignResponse {

    // 로그인 성공 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignInResult {
        private String userName;
        private String token;
    }

    // 닉네임 중복 확인 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NicknameCheckResult {
        private Boolean available;
    }
}