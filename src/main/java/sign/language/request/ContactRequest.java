package sign.language.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequest {
    @NotBlank(message = "연락처 전화번호는 필수 입력 항목입니다.")
    private String phoneNumber;

    @NotBlank(message = "연락처 이름은 필수 입력 항목입니다.")
    private String contactName;

    private String profileImageUrl;
}