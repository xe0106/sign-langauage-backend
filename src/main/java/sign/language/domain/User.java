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
import java.time.ZoneId;
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
    @JoinColumn(name = "receiver_id")
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

    /**
     * 퀴즈 정답 제출 시 실행 (개당 20% 상승: 0 -> 20 -> 40 -> 60 -> 80 -> 100)
     */
    public void recordQuizCorrect() {
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(zoneId);

        if (this.learningDays == null) this.learningDays = 0;
        if (this.learningPercentage == null) this.learningPercentage = 0;

        LocalDate previousActivityDate = (this.updatedAt != null)
                ? this.updatedAt.atZone(zoneId).toLocalDate()
                : null;

        // 날짜가 바뀌었으면(오늘 첫 퀴즈 활동이면) 오늘 달성률 0%로 리셋
        if (previousActivityDate == null || !previousActivityDate.isEqual(today)) {
            this.learningPercentage = 0;
        }

        int currentPercentage = this.learningPercentage;
        // 퀴즈 1문제당 +20% (최대 100%)
        int newPercentage = Math.min(100, currentPercentage + 20);

        // 오늘 "처음으로 100% 달성한 순간"에만 Streak 계산 및 반영
        if (currentPercentage < 100 && newPercentage == 100) {
            if (previousActivityDate.isEqual(today.minusDays(1))) {
                this.learningDays += 1; // 연속 달성
            } else {
                this.learningDays = 1;  // 신규/끊긴 후 재달성
            }
        }

        this.learningPercentage = newPercentage;
        this.updatedAt = Instant.now();
    }
}