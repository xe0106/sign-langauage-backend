package sign.language.response;

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
public class ContactCreateResponse {

    private Long contactId;
    private Long userId;
    private Long targetUserId;
    private String contactName;
    private Instant createdAt;

    public static ContactCreateResponse from(Contact contact) {
        Long targetUserId = (contact.getTargetUser() != null) ? contact.getTargetUser().getId() : null;

        return ContactCreateResponse.builder()
                .contactId(contact.getId())
                .userId(contact.getUser().getId())
                .targetUserId(targetUserId)
                .contactName(contact.getContactName())
                .createdAt(contact.getCreatedAt())
                .build();
    }
}