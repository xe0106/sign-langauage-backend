package sign.language.controller;

import org.springframework.web.bind.annotation.*;
import sign.language.domain.User;
import sign.language.request.SignInRequest;
import sign.language.request.SignUpRequest;
import sign.language.response.SignResponse;
import sign.language.service.SignService;

@RestController
@RequestMapping("/sign/language/auth")
public class SignController {

    private final SignService signService;

    public SignController(SignService signService) {
        this.signService = signService;
    }

    // 닉네임 중복 확인 API
    @GetMapping("/check-nickname")
    public SignResponse checkNickname(@RequestParam String nickname) {
        boolean available = signService.checkNicknameAvailable(nickname);
        if (available) {
            return new SignResponse(true, "사용 가능한 닉네임입니다.", true);
        } else {
            return new SignResponse(false, "이미 사용 중인 닉네임입니다.", false);
        }
    }

    // 회원가입 API
    @PostMapping("/signup")
    public SignResponse signUp(@RequestBody SignUpRequest request) {
        boolean success = signService.signUp(request);

        if (success) {
            return new SignResponse(true, "회원가입이 완료되었습니다.", request.getName());
        } else {
            return new SignResponse(false, "이미 가입된 이메일입니다.");
        }
    }

    // 로그인 API
    @PostMapping("/signin")
    public SignResponse signIn(@RequestBody SignInRequest request) {
        String token = signService.signIn(request.getEmail(), request.getPassword());

        if (token != null) {
            User user = signService.findByEmail(request.getEmail());
            return new SignResponse(true, "로그인 성공!", user.getName(), token);
        } else {
            return new SignResponse(false, "이메일 또는 비밀번호가 잘못되었습니다.");
        }
    }
}