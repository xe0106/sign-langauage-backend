package sign.language.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class QuizSubmitRequest {

    @NotNull(message = "선택한 답안 인덱스는 필수입니다.")
    private Integer selectedIndex;
}