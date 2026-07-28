package sign.language.response;

import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
public class LecturePageResponse extends PageResponse<LectureResponse> {

    private final long completedCount; // 💡 렉처 전용 완강 횟수 추가

    public LecturePageResponse(Page<LectureResponse> page, long completedCount) {
        super(page); // 기존 PageResponse의 content, pageNumber, totalElements 등 필드 초기화
        this.completedCount = completedCount;
    }

    public static LecturePageResponse of(Page<LectureResponse> page, long completedCount) {
        return new LecturePageResponse(page, completedCount);
    }
}