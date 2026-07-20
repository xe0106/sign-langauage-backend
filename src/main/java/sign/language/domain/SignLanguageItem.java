package sign.language.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "sign_language_item")
public class SignLanguageItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sign_id", nullable = false)
    private Long id;

    public enum Type {
        FINGER_ALPHABET, WORD, SENTENCE
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private Type type;

    @Column(name = "korean_text", nullable = false, length = 100)
    private String koreanText;

    public enum Category {
        BASIC, DAILY, EMOTION, FAMILY, NUMBER
    }

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'BASIC'")
    @Column(name = "category", length = 20)
    private Category category;

    @Column(name = "image_3d_url")
    private String image3dUrl;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "description")
    private String description;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at")
    private Instant createdAt;

    @NonNull
    @OneToMany
    @JoinColumn(name = "target_sign_id")
    private Set<Quiz> quizzes = new LinkedHashSet<>();
}