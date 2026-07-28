package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sign.language.domain.UserLectureProgress;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LectureProgressResponse {

    private Long progressId;
    private Long userId;
    private Long lectureId;
    private Boolean isCompleted;
    private Instant lastWatchedAt;

    public static LectureProgressResponse from(UserLectureProgress progress) {
        return LectureProgressResponse.builder()
                .progressId(progress.getId())
                .userId(progress.getUser().getId())
                .lectureId(progress.getLecture().getId())
                .isCompleted(progress.getIsCompleted())
                .lastWatchedAt(progress.getLastWatchedAt())
                .build();
    }
}