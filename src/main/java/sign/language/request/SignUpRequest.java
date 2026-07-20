package sign.language.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import sign.language.domain.User;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class SignUpRequest {
    private String email;
    private String password;
    private String name;
    private String nickname;
    private User.Gender gender;
    private LocalDate birthDate;
    private String phoneNumber;
}