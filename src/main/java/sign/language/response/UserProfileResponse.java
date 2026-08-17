package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {
    private Long userId;
    private String email;
    private String name;
    private String profileImageUrl;
    private Integer learningDays;
    private List<DailyAttendanceDto> weeklyAttendance;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailyAttendanceDto {
        private String dayOfWeek;       // MON, TUE, WED, THU, FRI, SAT, SUN
        private LocalDate date;         // 2026-08-17
        private boolean isAttended;     // 출석 인정 여부 (achievementRate >= 100)
        private int achievementRate;    // 당일 달성률 (0~100)
    }
}