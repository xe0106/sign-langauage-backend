package sign.language.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;
import sign.language.domain.User;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class ProfileModifyRequest {
    private String nickname;
    private User.Gender gender;
    private LocalDate birthDate;
    private String phoneNumber;

    @URL(message = "올바른 이미지 URL 형식이 아닙니다.")
    private String profileImageUrl;
}