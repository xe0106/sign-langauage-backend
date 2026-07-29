package sign.language.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sign.language.errorcode.ErrorStatus;
import sign.language.exception.SignException;
import sign.language.request.ProfileModifyRequest;
import sign.language.request.SignInRequest;
import sign.language.request.SignOffRequest;
import sign.language.request.SignUpRequest;
import sign.language.response.ApiResponse;
import sign.language.response.SignResponse;
import sign.language.service.SignService;

@RestController
@RequestMapping("/sign/language/auth")
public class SignController {

    private final SignService signService;

    public SignController(SignService signService) {
        this.signService = signService;
    }

    // 회원가입 API
    @PostMapping("/signup")
    public ApiResponse<String> signUp(@Valid @RequestBody SignUpRequest request) {
        String userName = signService.signUp(request);
        return ApiResponse.onSuccess(userName);
    }

    // 로그인 API
    @PostMapping("/signin")
    public ApiResponse<SignResponse.SignInResult> signIn(@Valid @RequestBody SignInRequest request) {
        SignResponse.SignInResult result = signService.signIn(request.getEmail(), request.getPassword());
        return ApiResponse.onSuccess(result);
    }

    // 닉네임 중복 확인 API
    @GetMapping("/check-nickname")
    public ApiResponse<String> checkNickname(
            @RequestParam("nickname") String nickname
    ) {
        if (!org.springframework.util.StringUtils.hasText(nickname)) {
            throw new SignException(ErrorStatus.INVALID_INPUT_VALUE);
        }

        String available = signService.checkNicknameDuplicate(nickname.trim());
        return ApiResponse.onSuccess(available);
    }

    // 로그아웃
    @PostMapping("/signout")
    public ApiResponse<String> signOut(@AuthenticationPrincipal String email) {
        signService.signOut(email);
        return ApiResponse.onSuccess();
    }

    // 회원탈퇴 API
    @DeleteMapping("/signoff")
    public ApiResponse<String> signOff(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody SignOffRequest request
    ) {
        // 토큰의 이메일과 입력받은 비밀번호로 탈퇴 처리
        signService.signOff(email, request.getPassword());
        return ApiResponse.onSuccess();
    }

    // 프로필 정보 수정 API (로그인 필수)
    @PatchMapping("/modify")
    public ApiResponse<String> signModify(
            @AuthenticationPrincipal String email,
            @RequestBody ProfileModifyRequest request
    ) {
        // 이미 인증을 통과하고 이메일이 들어왔으므로 바로 서비스 호출
        signService.signModify(email, request);
        return ApiResponse.onSuccess();
    }
}