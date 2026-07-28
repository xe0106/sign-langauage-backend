package sign.language.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sign.language.response.ApiResponse;
import sign.language.response.UserProfileResponse;
import sign.language.service.UserProfileService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sign/language/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * 내 프로필 및 학습 정보 조회
     */
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal String email // 기존 회원탈퇴 방식과 동일하게 이메일 추출
    ) {
        UserProfileResponse response = userProfileService.getUserProfileByEmail(email);
        return ApiResponse.onSuccess(response);
    }

    /**
     * 특정 사용자(남) 프로필 조회
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserProfileResponse> getUserProfile(
            @PathVariable Long userId
    ) {
        // Service에 PathVariable로 받은 userId를 전달
        UserProfileResponse response = userProfileService.getUserProfileByUserId(userId);
        return ApiResponse.onSuccess(response);
    }
}