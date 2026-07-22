package sign.language.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import sign.language.domain.User;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class ProfileModifyRequest {
    private String nickname;
    private User.Gender gender;
    private LocalDate birthDate;
    private String phoneNumber;
}