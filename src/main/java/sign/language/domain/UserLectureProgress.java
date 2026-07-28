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
@Table(name = "user_lecture_progress") // 사용자와 강의 엔티티 간 결합 테이블
public class UserLectureProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "progress_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @ColumnDefault("0")
    @Column(name = "is_completed")
    private Boolean isCompleted;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "last_watched_at")
    private Instant lastWatchedAt;

    // 최초 수강 기록 생성
    public static UserLectureProgress createProgress(User user, Lecture lecture) {
        UserLectureProgress progress = new UserLectureProgress();
        progress.user = user;
        progress.lecture = lecture;
        progress.isCompleted = false;
        progress.lastWatchedAt = Instant.now();
        return progress;
    }

    // 수강 완료 상태 변경
    public void complete() {
        this.isCompleted = true;
        this.lastWatchedAt = Instant.now();
    }
}