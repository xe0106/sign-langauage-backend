package sign.language.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sign.language.domain.User;
import sign.language.domain.UserAttendance;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserAttendanceRepository extends JpaRepository<UserAttendance, Long> {

    // 특정 날짜 출석 기록 조회
    Optional<UserAttendance> findByUserAndAttendanceDate(User user, LocalDate attendanceDate);

    // 이번 주(시작일~종료일) 출석 기록 조회 (월~일 UI 렌더링용)
    List<UserAttendance> findAllByUserAndAttendanceDateBetween(User user, LocalDate startDate, LocalDate endDate);
}