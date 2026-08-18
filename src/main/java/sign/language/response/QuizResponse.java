package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sign.language.domain.Lecture;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponse {

    private Long quizId;
    private String questionText;
    private Long targetSignId;
    private String image3dUrl;
    private List<String> options;
    private Integer correctOptionIndex;

    public static QuizResponse of(Lecture target, List<String> options, int correctOptionIndex) {
        return QuizResponse.builder()
                .quizId(target.getId())
                .questionText("다음 동작이 의미하는 단어는 무엇인가요?")
                .targetSignId(target.getId())
                .image3dUrl(target.getQuizImageUrl())
                .options(options)
                .correctOptionIndex(correctOptionIndex)
                .build();
    }
}