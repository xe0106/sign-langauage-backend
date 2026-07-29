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
    private Integer learningDays;
}