package sign.language.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import lombok.NonNull;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "lecture")
public class Lecture {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lecture_id", nullable = false)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", length = 400)
    private String description;

    public enum Category {
        BASIC, NUMBER, FAMILY, EMOTION, GREETING,
        LOCATION, FOOD, SOCIETY, ECONOMY, EDUCATION,
        RELIGION, HOUSING, ANIMAL_PLANT, POLITICS, NATURE,
        CLOTHING, CULTURE, LIFE, CONCEPT, HUMAN, DAILY
    }

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'DAILY'")
    @Column(name = "category", length = 20)
    private Category category;

    @Column(name = "thumbnail_url", length = 400)
    private String thumbnailUrl;

    @Column(name = "quiz_image_url", length = 400)
    private String quizImageUrl;

    @Column(name = "video_url")
    private String videoUrl;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at")
    private Instant createdAt;

    @NonNull
    @OneToMany(mappedBy = "lecture")
    private Set<UserLectureProgress> userLectureProgresses = new LinkedHashSet<>();
}