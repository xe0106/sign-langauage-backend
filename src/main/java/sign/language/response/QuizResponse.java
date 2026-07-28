package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sign.language.domain.Quiz;

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

    public static QuizResponse from(Quiz quiz) {
        return QuizResponse.builder()
                .quizId(quiz.getId())
                .questionText(quiz.getQuestionText())
                .targetSignId(quiz.getTargetSign() != null ? quiz.getTargetSign().getId() : null)
                .image3dUrl(quiz.getImage3dUrl())
                .options(quiz.getOptions())
                .correctOptionIndex(quiz.getCorrectOptionIndex())
                .build();
    }
}