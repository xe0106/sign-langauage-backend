package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {
    private Long userId;
    private String email;
    private String name;
    private String profileImageUrl;
    private Integer learningDays; // 학습 일수 (사진의 '연속 학습 12일')
}