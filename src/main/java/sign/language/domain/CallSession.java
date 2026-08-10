package sign.language.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "call_session")
public class CallSession {
    @Id
    @Column(name = "call_id", nullable = false, length = 36)
    private String callId;

    public enum Status {
        RINGING, CONNECTED, ENDED, REJECTED
    }

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'RINGING'")
    @Column(name = "status")
    private Status status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "caller_id", nullable = false)
    private User caller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    // ⭐️ 생성 전용 정적 팩토리 메서드
    public static CallSession create(User caller, User receiver) {
        CallSession session = new CallSession();
        session.callId = UUID.randomUUID().toString();
        session.caller = caller;
        session.receiver = receiver;
        session.status = Status.RINGING;
        session.startedAt = Instant.now();
        session.createdAt = Instant.now();
        return session;
    }

    // ⭐️ 상태 변경 비즈니스 메서드
    public void updateStatus(Status newStatus) {
        this.status = newStatus;
        if (newStatus == Status.ENDED || newStatus == Status.REJECTED) {
            this.endedAt = Instant.now();
        }
    }
}