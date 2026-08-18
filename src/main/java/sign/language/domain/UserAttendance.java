package sign.language.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "user_attendance",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_date", columnNames = {"user_id", "attendance_date"})
        }
)
public class UserAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "achievement_rate", nullable = false)
    private Integer achievementRate; // 당일 달성률 (0~100)

    public static UserAttendance create(User user, LocalDate date, int initialRate) {
        UserAttendance record = new UserAttendance();
        record.user = user;
        record.attendanceDate = date;
        record.achievementRate = initialRate;
        return record;
    }

    public void updateAchievementRate(int newRate) {
        this.achievementRate = newRate;
    }
}