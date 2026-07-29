package sign.language.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "contact")
public class Contact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contact_id", nullable = false)
    private Long id;

    @Column(name = "contact_name", nullable = false, length = 50)
    private String contactName;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "last_contacted_at")
    private Instant lastContactedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    public static Contact createContact(User user, User targetUser, String customName, String customImageUrl) {
        Contact contact = new Contact();
        contact.user = user;
        contact.targetUser = targetUser;
        contact.contactName = customName;
        contact.profileImageUrl = customImageUrl;

        // 생성 시각
        contact.createdAt = Instant.now();

        return contact;
    }

    public void updateLastContactedAt() {
        this.lastContactedAt = Instant.now();
    }
}