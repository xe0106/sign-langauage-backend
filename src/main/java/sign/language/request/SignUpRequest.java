package sign.language.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;
import sign.language.domain.User;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class SignUpRequest {
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    private String password;

    @NotBlank(message = "이름은 필수 입력값입니다.")
    private String name;

    @NotBlank(message = "닉네임은 필수 입력값입니다.")
    private String nickname;

    @NotNull(message = "성별을 선택해주세요.")
    private User.Gender gender;

    @NotNull(message = "생년월일을 입력해주세요.")
    private LocalDate birthDate;

    @NotBlank(message = "전화번호는 필수 입력값입니다.")
    private String phoneNumber;

    @URL(message = "올바른 이미지 URL 형식이 아닙니다.")
    private String profileImageUrl;
}