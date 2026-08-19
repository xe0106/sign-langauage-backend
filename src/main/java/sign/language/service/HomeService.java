package sign.language.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sign.language.domain.User;
import sign.language.response.HomeResponse;
import sign.language.errorcode.ErrorStatus;
import sign.language.exception.SignException;
import sign.language.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private final UserRepository userRepository;

    public HomeResponse getHomeData(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SignException(ErrorStatus.DELETED_MEMBER));

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        // 날짜 포맷팅
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd (E)", Locale.KOREAN);
        String currentDate = now.format(formatter);

        // 시간대별 인삿말 생성
        String greetingMessage = generateGreeting(now.getHour(), user.getName());

        // 학습 진행도 (User 엔티티의 learningPercentage 필드 기준)
        Integer progressPercentage = user.getLearningPercentage() != null ? user.getLearningPercentage() : 0;

        return HomeResponse.builder()
                .currentDate(currentDate)
                .greetingMessage(greetingMessage)
                .goalTitle("수어 단어 5개 익히기")
                .progressPercentage(progressPercentage)
                .build();
    }

    private String generateGreeting(int hour, String userName) {
        String timeGreeting;
        if (hour >= 5 && hour < 12) {
            timeGreeting = "좋은 아침이에요";
        } else if (hour >= 12 && hour < 18) {
            timeGreeting = "즐거운 오후예요";
        } else if (hour >= 18 && hour < 22) {
            timeGreeting = "편안한 저녁이에요";
        } else {
            timeGreeting = "조용한 밤이에요";
        }

        return String.format("%s, %s님!", timeGreeting, userName);
    }
}