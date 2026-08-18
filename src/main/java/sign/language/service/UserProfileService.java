package sign.language.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sign.language.domain.User;
import sign.language.domain.UserAttendance;
import sign.language.errorcode.ErrorStatus;
import sign.language.exception.UserProfileException;
import sign.language.repository.UserAttendanceRepository;
import sign.language.repository.UserRepository;
import sign.language.response.UserProfileResponse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserAttendanceRepository userAttendanceRepository;

    @Value("${app.default-profile-image}")
    private String defaultProfileImage;

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfileByEmail(String email) {
        // 이메일로 회원 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserProfileException(ErrorStatus.MEMBER_NOT_FOUND));

        if (user.getStatus() == User.Status.DELETED) {
            throw new UserProfileException(ErrorStatus.DELETED_MEMBER);
        }

        return buildUserProfileResponse(user);
    }

    // 특정 사용자 프로필 조회 (userId 기반)
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfileByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserProfileException(ErrorStatus.MEMBER_NOT_FOUND));

        if (user.getStatus() == User.Status.DELETED) {
            throw new UserProfileException(ErrorStatus.DELETED_MEMBER);
        }

        return buildUserProfileResponse(user);
    }

    /**
     * User 엔티티와 주간 출석 기록을 결합하여 UserProfileResponse DTO를 생성
     */
    private UserProfileResponse buildUserProfileResponse(User user) {
        // 프로필 이미지가 없으면 기본 이미지 URL 사용
        String imageUrl = (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isBlank())
                ? user.getProfileImageUrl()
                : defaultProfileImage;

        // 이번 주 월요일 ~ 일요일 7일치 출석 목록 생성
        List<UserProfileResponse.DailyAttendanceDto> weeklyAttendance = getWeeklyAttendanceList(user);

        return UserProfileResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .profileImageUrl(imageUrl)
                .learningDays(user.getLearningDays() != null ? user.getLearningDays() : 0)
                .weeklyAttendance(weeklyAttendance)
                .build();
    }

    /**
     * 이번 주(월~일)의 날짜별 출석 달성 현황(7개 항목)을 조회하여 반환
     */
    private List<UserProfileResponse.DailyAttendanceDto> getWeeklyAttendanceList(User user) {
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(zoneId);

        // 이번 주 월요일과 일요일 계산
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = today.with(DayOfWeek.SUNDAY);

        // 이번 주에 기록된 출석 이력 조회
        List<UserAttendance> attendances = userAttendanceRepository
                .findAllByUserAndAttendanceDateBetween(user, monday, sunday);

        // 빠른 조회를 위해 날짜 -> 달성률 Map으로 변환
        Map<LocalDate, Integer> attendanceMap = attendances.stream()
                .collect(Collectors.toMap(
                        UserAttendance::getAttendanceDate,
                        UserAttendance::getAchievementRate,
                        (existing, replacement) -> existing
                ));

        // 월요일부터 일요일까지 7일 순회하며 DTO 생성
        List<UserProfileResponse.DailyAttendanceDto> weeklyList = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate targetDate = monday.plusDays(i);
            int rate = attendanceMap.getOrDefault(targetDate, 0);

            weeklyList.add(UserProfileResponse.DailyAttendanceDto.builder()
                    .dayOfWeek(targetDate.getDayOfWeek().name().substring(0, 3)) // "MON", "TUE", ...
                    .date(targetDate)
                    .isAttended(rate >= 100) // 100% 이상일 때 출석 도장 인정
                    .achievementRate(rate)
                    .build());
        }

        return weeklyList;
    }
}