package sign.language.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sign.language.response.ApiResponse;
import sign.language.response.LectureProgressResponse;
import sign.language.response.LectureResponse;
import sign.language.response.PageResponse;
import sign.language.service.LectureService;


@RestController
@RequiredArgsConstructor
@RequestMapping("/sign/language/lectures")
public class LectureController {

    private final LectureService lectureService;

    /**
     * 강의 목록 조회 API (전체 조회 및 카테고리 필터링 통합)
     * 예시: GET /sign/language/lectures
     * 예시: GET /sign/language/lectures?category=BASIC
     * 예시: GET /sign/language/lectures?category=BASIC&page=0&size=10
     */
    @GetMapping
    public ApiResponse<PageResponse<LectureResponse>> getLectures(
            @AuthenticationPrincipal String email,
            @RequestParam(name = "category", required = false) String category,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.onSuccess(lectureService.getLectures(email, category, pageable));
    }

    /**
     * 단건 강의 상세 조회
     * 예시: GET /sign/language/lectures/1
     */
    @GetMapping("/{lectureId}")
    public ApiResponse<LectureResponse> getLectureDetail(
            @AuthenticationPrincipal String email,
            @PathVariable Long lectureId
    ) {
        return ApiResponse.onSuccess(lectureService.getLectureDetail(email, lectureId));
    }

    /**
     * 강의 수강 완료 처리 API
     * 예시: POST /sign/language/lectures/1/progress
     */
    @PostMapping("/{lectureId}/progress")
    public ApiResponse<LectureProgressResponse> completeLecture(
            @AuthenticationPrincipal String email,
            @PathVariable Long lectureId
    ) {
        return ApiResponse.onSuccess(lectureService.completeLecture(email, lectureId));
    }
}