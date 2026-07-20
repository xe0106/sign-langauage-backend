package sign.language.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
    private Long id;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    public enum Gender {
        MALE, FEMALE
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @ColumnDefault("0")
    @Column(name = "learning_days")
    private Integer learningDays;

    @ColumnDefault("1")
    @Column(name = "notification_enabled")
    private Boolean notificationEnabled;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at")
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at")
    private Instant updatedAt;

    @NonNull
    @OneToMany
    @JoinColumn(name = "caller_id")
    private Set<CallSession> callSessions1 = new LinkedHashSet<>();

    @NonNull
    @OneToMany
    @JoinColumn(name = "callee_id")
    private Set<CallSession> callSessions2 = new LinkedHashSet<>();

    @NonNull
    @OneToMany
    @JoinColumn(name = "sender_id")
    private Set<CallSubtitle> callSubtitles = new LinkedHashSet<>();

    @NonNull
    @OneToMany
    @JoinColumn(name = "user_id")
    private Set<Contact> contacts1 = new LinkedHashSet<>();

    @NonNull
    @OneToMany
    @JoinColumn(name = "target_user_id")
    private Set<Contact> contacts2 = new LinkedHashSet<>();

    @NonNull
    @OneToMany(mappedBy = "user")
    private Set<UserLectureProgress> userLectureProgresses = new LinkedHashSet<>();
}