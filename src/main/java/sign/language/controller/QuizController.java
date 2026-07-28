package sign.language.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sign.language.request.QuizSubmitRequest;
import sign.language.response.ApiResponse;
import sign.language.response.QuizResponse;
import sign.language.response.QuizSubmitResponse;
import sign.language.service.QuizService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sign/language/quizzes")
public class QuizController {

    private final QuizService quizService;

    /**
     * 수어 학습 테스트를 위한 퀴즈 목록 조회
     * 예시: GET /sign/language/quizzes?count=5
     */
    @GetMapping
    public ApiResponse<List<QuizResponse>> getQuizzes(
            @RequestParam(name = "count", required = false, defaultValue = "10") Integer count
    ) {
        List<QuizResponse> responses = quizService.getQuizzes(count);
        return ApiResponse.onSuccess(responses);
    }

    /**
     * 퀴즈 답안 제출 및 채점 API
     * 예시: POST /sign/language/quizzes/1/submit
     */
    @PostMapping("/{quizId}/submit")
    public ApiResponse<QuizSubmitResponse> submitQuiz(
            @AuthenticationPrincipal String email,
            @PathVariable Long quizId,
            @Valid @RequestBody QuizSubmitRequest request
    ) {
        QuizSubmitResponse response = quizService.submitQuiz(email, quizId, request);
        return ApiResponse.onSuccess(response);
    }
}