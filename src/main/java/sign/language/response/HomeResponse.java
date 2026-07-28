package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeResponse {

    private String currentDate;         // 예: "05/23 (토)"
    private String greetingMessage;     // 예: "좋은 아침이에요, 배정환님!"
    private String goalTitle;           // 예: "수어 단어 5개 익히기"
    private Integer progressPercentage; // 예: 40
}