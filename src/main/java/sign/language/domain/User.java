package sign.language.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import lombok.NonNull;
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

    public enum Status {
        LOGIN, LOGOUT, DELETED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @ColumnDefault("0")
    @Column(name = "learning_days")
    private Integer learningDays;

    @ColumnDefault("0")
    @Column(name = "learning_percentage")
    private Integer learningPercentage;

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

    public static User create(String email, String encodedPassword, String name, String nickname,
                              Gender gender, LocalDate birthDate, String phoneNumber, String profileImageUrl) {
        User user = new User();
        user.email = email;
        user.passwordHash = encodedPassword;
        user.name = name;
        user.nickname = nickname;
        user.gender = gender;
        user.birthDate = birthDate;
        user.phoneNumber = phoneNumber;

        // 초기 기본값 설정
        user.learningDays = 0;
        user.learningPercentage = 0;
        user.notificationEnabled = true;
        user.status = Status.LOGOUT;
        user.createdAt = Instant.now();
        user.updatedAt = Instant.now();
        user.profileImageUrl = profileImageUrl;

        return user;
    }

    public void updateProfile(String nickname, Gender gender, LocalDate birthDate, String phoneNumber, String profileImageUrl) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
        this.updatedAt = Instant.now();
    }

    public void withdraw() {
        // 이메일 중복 방지 및 식별 불가능하도록 익명화
        this.email = "deleted_" + this.id + "@deleted.com";

        // 비밀번호 민감 정보 초기화
        this.passwordHash = "";

        // 닉네임 및 개인정보 변경
        this.nickname = "탈퇴한 회원" + getId();
        this.name = "탈퇴자" + getId();
        this.phoneNumber = null;
        this.status = Status.DELETED;

        // 탈퇴/수정 시각 업데이트
        this.updatedAt = Instant.now();
        this.profileImageUrl = null;
    }

    public void setLogin() {
        this.status = Status.LOGIN;
    }

    public void setLogOut() {
        this.status = Status.LOGOUT;
    }
}