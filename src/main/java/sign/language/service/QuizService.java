package sign.language.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sign.language.domain.Lecture;
import sign.language.domain.User;
import sign.language.errorcode.ErrorStatus;
import sign.language.exception.QuizException;
import sign.language.repository.LectureRepository;
import sign.language.repository.UserRepository;
import sign.language.request.QuizSubmitRequest;
import sign.language.response.QuizResponse;
import sign.language.response.QuizSubmitResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QUIZ_ANSWER_KEY_PREFIX = "QUIZ_ANSWER:";
    private static final long QUIZ_TTL_MINUTES = 30; // 퀴즈 하나당 제한시간 30분

    /**
     * 동적 퀴즈 목록 생성 및 조회 (유저별 Key 저장), 퀴즈 테이블 제거
     */
    public List<QuizResponse> getQuizzes(String email, Integer count) {
        int quizCount = (count == null) ? 5 : count;

        if (quizCount < 1 || quizCount > 20) {
            throw new QuizException(ErrorStatus.QUIZ_INVALID_COUNT); // QUIZ400
        }

        // 정답 강의 N개 무작위 추출
        List<Lecture> targetLectures = lectureRepository.findRandomLectures(PageRequest.of(0, quizCount));

        return targetLectures.stream().map(target -> {
            Long quizId = target.getId();

            List<Lecture> wrongLectures = lectureRepository.findRandomLecturesExcluding(
                    List.of(quizId),
                    PageRequest.of(0, 3)
            );

            // 정답 문자열 정제 (변수에 확실히 보관)
            String correctTitle = formatQuizTitle(target.getTitle());

            // 보기 구성
            List<String> options = new ArrayList<>();
            options.add(correctTitle);
            wrongLectures.forEach(w -> options.add(formatQuizTitle(w.getTitle())));

            // 보기 무작위 셔플
            Collections.shuffle(options);

            // 셔플된 options 리스트를 순회하며 정답과 똑같은 텍스트의 index 추출
            int correctOptionIndex = -1;
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).equals(correctTitle)) {
                    correctOptionIndex = i + 1;
                    break;
                }
            }

            // Redis 저장
            String redisKey = QUIZ_ANSWER_KEY_PREFIX + email + ":" + quizId;
            redisTemplate.opsForValue().set(redisKey, String.valueOf(correctOptionIndex), QUIZ_TTL_MINUTES, TimeUnit.MINUTES);

            return QuizResponse.of(target, options, correctOptionIndex);
        }).toList();
    }

    /**
     * 퀴즈 답안 제출 및 채점
     */
    @Transactional
    public QuizSubmitResponse submitQuiz(String email, Long quizId, QuizSubmitRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new QuizException(ErrorStatus.DELETED_MEMBER));

        // quizId(=lectureId)로 해당 정답 강의 조회
        Lecture targetLecture = lectureRepository.findById(quizId)
                .orElseThrow(() -> new QuizException(ErrorStatus.QUIZ_NOT_FOUND));

        // 유저 식별키로 Redis에서 정답 인덱스 조회
        String redisKey = QUIZ_ANSWER_KEY_PREFIX + email + ":" + quizId;
        Object cachedAnswer = redisTemplate.opsForValue().get(redisKey);

        // 만료되었거나 없을 시 0으로 때우지 않고 에러 처리 or 기본값 처리
        if (cachedAnswer == null) {
            throw new QuizException(ErrorStatus.QUIZ_NOT_FOUND); // 혹은 만료 예외 처리
        }

        int correctOptionIndex = Integer.parseInt(cachedAnswer.toString());

        // 제출된 selectedIndex와 Redis 정답 인덱스 비교
        boolean isCorrect = request.getSelectedIndex() != null
                && request.getSelectedIndex().equals(correctOptionIndex);

        String formattedTitle = formatQuizTitle(targetLecture.getTitle());
        String explanation = isCorrect
                ? "정답입니다! '" + formattedTitle + "' 수어 동작입니다."
                : "오답입니다. 정답은 '" + formattedTitle + "' 입니다.";

        if (isCorrect) {
            user.recordQuizCorrect();
        }

        // 3. 채점 완료 후 정답 Key 삭제
        redisTemplate.delete(redisKey);

        return QuizSubmitResponse.of(quizId, isCorrect, correctOptionIndex, explanation);
    }

    /**
     * 퀴즈용 단어 정제 메서드
     * 1. 동사/형용사(~하다, ~이다 등) 또는 친숙한 표현 우선 탐색
     * 2. 최대 2개 단어만 추출하여 " / " 형태로 조합
     */
    private String formatQuizTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) return "";

        String[] words = rawTitle.split(",");

        // 단어가 1개면 바로 반환
        if (words.length == 1) {
            return words[0].trim();
        }

        List<String> trimmedWords = Arrays.stream(words)
                .map(String::trim)
                .filter(w -> !w.isBlank())
                .toList();

        // 서술어/동사 형태(~하다, ~되다, ~이다)나 친숙한 표현을 우선순위로 정렬
        List<String> prioritized = new ArrayList<>(trimmedWords);
        prioritized.sort((w1, w2) -> {
            boolean isEasy1 = w1.endsWith("하다") || w1.endsWith("되다") || w1.endsWith("이다");
            boolean isEasy2 = w2.endsWith("하다") || w2.endsWith("되다") || w2.endsWith("이다");
            if (isEasy1 && !isEasy2) return -1; // w1을 앞으로
            if (!isEasy1 && isEasy2) return 1;  // w2를 앞으로
            return 0; // 기존 순서 유지
        });

        // 상위 최대 2개 추출 후 " / " 형태로 결합
        List<String> top2 = prioritized.stream()
                .limit(2)
                .toList();

        return String.join(" / ", top2);
    }
}