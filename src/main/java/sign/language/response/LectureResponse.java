package sign.language.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sign.language.domain.Lecture;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LectureResponse {

    private Long lectureId;
    private String title;
    private String description;
    private String category;
    private String thumbnailUrl;
    private String videoUrl;
    private Instant createdAt;
    private Boolean isCompleted;

    public static LectureResponse of(Lecture lecture, boolean isCompleted) {
        return LectureResponse.builder()
                .lectureId(lecture.getId())
                .title(lecture.getTitle())
                .description(lecture.getDescription())
                .category(lecture.getCategory() != null ? lecture.getCategory().name() : null)
                .thumbnailUrl(lecture.getThumbnailUrl())
                .videoUrl(lecture.getVideoUrl())
                .createdAt(lecture.getCreatedAt())
                .isCompleted(isCompleted)
                .build();
    }
}