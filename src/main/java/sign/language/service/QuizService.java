package sign.language.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sign.language.domain.Quiz;
import sign.language.domain.User;
import sign.language.errorcode.ErrorStatus;
import sign.language.exception.QuizException;
import sign.language.repository.QuizRepository;
import sign.language.repository.UserRepository;
import sign.language.request.QuizSubmitRequest;
import sign.language.response.QuizResponse;
import sign.language.response.QuizSubmitResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private final QuizRepository quizRepository;
    private final UserRepository userRepository;

    /**
     * 퀴즈 목록 조회 (개수 제한: 1 ~ 20개)
     */
    public List<QuizResponse> getQuizzes(Integer count) {
        int quizCount = (count == null) ? 5 : count; // 하루 당 퀴즈 할당량은 5개

        // 개수 유효성 검증 (1개 이상 20개 이하)
        if (quizCount < 1 || quizCount > 20) {
            throw new QuizException(ErrorStatus.QUIZ_INVALID_COUNT); // QUIZ400
        }

        // DB에서 지정한 개수만큼 랜덤 조회
        List<Quiz> quizzes = quizRepository.findRandomQuizzes(PageRequest.of(0, quizCount));

        return quizzes.stream()
                .map(QuizResponse::from)
                .toList();
    }

    /**
     * 퀴즈 답안 제출 및 채점
     */
    @Transactional
    public QuizSubmitResponse submitQuiz(String email, Long quizId, QuizSubmitRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new QuizException(ErrorStatus.DELETED_MEMBER));

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new QuizException(ErrorStatus.QUIZ_NOT_FOUND));

        boolean isCorrect = quiz.getCorrectOptionIndex().equals(request.getSelectedIndex());

        // List<String>에서 정답 텍스트 바로 가공
        String correctOptionText = "";
        if (quiz.getOptions() != null && quiz.getCorrectOptionIndex() < quiz.getOptions().size()) {
            correctOptionText = quiz.getOptions().get(quiz.getCorrectOptionIndex());
        }

        String explanation = isCorrect
                ? "정답입니다! '" + correctOptionText + "' 수어 동작입니다."
                : "오답입니다. 정답은 '" + correctOptionText + "' 입니다.";

        // 연속 학습 일수(Streak) 반영
        if (isCorrect) {
            user.recordQuizCorrect();
        }

        return QuizSubmitResponse.of(quizId, isCorrect, quiz.getCorrectOptionIndex(), explanation);
    }
}