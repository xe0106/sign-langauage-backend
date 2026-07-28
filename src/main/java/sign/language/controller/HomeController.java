package sign.language.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sign.language.response.HomeResponse;
import sign.language.response.ApiResponse;
import sign.language.service.HomeService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sign/language/home")
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    public ApiResponse<HomeResponse> getHomeInfo(
            @AuthenticationPrincipal String email
    ) {
        HomeResponse homeData = homeService.getHomeData(email);
        return ApiResponse.onSuccess(homeData);
    }
}