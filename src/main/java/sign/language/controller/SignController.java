package sign.language.controller;

import org.springframework.web.bind.annotation.*;
import sign.language.domain.User;
import sign.language.response.SignResponse;
import sign.language.service.SignService;
import java.util.Map;

@RestController
@RequestMapping("/sign/language/auth")
public class SignController {

    private final SignService signService;

    public SignController(SignService signService) {
        this.signService = signService;
    }

    @PostMapping("/signin")
    public SignResponse signIn(@RequestBody Map<String, String> request) {
        String id = request.get("id");
        String password = request.get("password");

        String token = signService.signIn(id, password);

        if (token != null) {
            User user = signService.findById(id);
            return new SignResponse(true, "로그인 성공!", user.getName(), token);
        } else {
            return new SignResponse(false, "아이디 또는 비밀번호가 잘못되었습니다.");
        }
    }

    @PostMapping("/signup")
    public SignResponse signUp(@RequestBody Map<String, String> request) {
        String id = request.get("id");
        String password = request.get("password");
        String name = request.get("name");

        boolean success = signService.signUp(id, password, name);

        if (success) {
            return new SignResponse(true, "회원가입이 완료되었습니다.", name);
        } else {
            return new SignResponse(false, "이미 가입된 아이디입니다.");
        }
    }
}