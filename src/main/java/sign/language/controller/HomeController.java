package sign.language.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sign.language.response.HomeResponse;
import sign.language.response.ApiResponse;
import sign.language.service.ContactService;
import sign.language.service.HomeService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sign/language/home")
public class HomeController {

    private final HomeService homeService;
    private final ContactService contactService;

    /**
     * 홈 화면 정보 확인
     * GET /sign/language/home
     */
    @GetMapping
    public ApiResponse<HomeResponse> getHomeInfo(
            @AuthenticationPrincipal String email
    ) {
        HomeResponse homeData = homeService.getHomeData(email);
        return ApiResponse.onSuccess(homeData);
    }

    /**
     * 최근 연락처 조회
     * GET /sign/language/home/recent?limit=5
     */
    @GetMapping("/recent")
    public ApiResponse<List<sign.language.dto.ContactResponse>> getRecentContacts(
            @AuthenticationPrincipal String email,
            @RequestParam(name = "limit", defaultValue = "5") int limit
    ) {
        List<sign.language.dto.ContactResponse> recentContacts = contactService.getRecentContacts(email, limit);
        return ApiResponse.onSuccess(recentContacts);
    }
}