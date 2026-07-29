package sign.language.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sign.language.domain.Contact;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactResponse {

    private Long contactId;
    private Long targetUserId;
    private String contactName;
    private String profileImageUrl;
    private Instant createdAt;
    private Instant lastContactedAt; // 최근 연락 시각

    public static ContactResponse from(Contact contact) {
        Long targetUserId = (contact.getTargetUser() != null) ? contact.getTargetUser().getId() : null;

        return ContactResponse.builder()
                .contactId(contact.getId())
                .targetUserId(targetUserId)
                .contactName(contact.getContactName())
                .profileImageUrl(contact.getProfileImageUrl())
                .createdAt(contact.getCreatedAt())
                .lastContactedAt(contact.getLastContactedAt() != null ? contact.getLastContactedAt() : contact.getCreatedAt())
                .build();
    }
}