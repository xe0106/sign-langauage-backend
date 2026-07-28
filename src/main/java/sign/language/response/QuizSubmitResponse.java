package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmitResponse {

    private Long quizId;
    private Boolean isCorrect;
    private Integer correctOptionIndex;
    private String explanation;

    public static QuizSubmitResponse of(Long quizId, boolean isCorrect, Integer correctOptionIndex, String explanation) {
        return QuizSubmitResponse.builder()
                .quizId(quizId)
                .isCorrect(isCorrect)
                .correctOptionIndex(correctOptionIndex)
                .explanation(explanation)
                .build();
    }
}