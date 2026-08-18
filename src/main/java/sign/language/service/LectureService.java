package sign.language.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sign.language.domain.Lecture;
import sign.language.domain.User;
import sign.language.domain.UserLectureProgress;
import sign.language.domain.Lecture.Category;
import sign.language.exception.LectureException;
import sign.language.errorcode.ErrorStatus;
import sign.language.repository.LectureRepository;
import sign.language.repository.UserLectureProgressRepository;
import sign.language.repository.UserRepository;
import sign.language.response.LecturePageResponse;
import sign.language.response.LectureProgressResponse;
import sign.language.response.LectureResponse;
import sign.language.response.PageResponse;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LectureService {

    private final LectureRepository lectureRepository;
    private final UserLectureProgressRepository progressRepository;
    private final UserRepository userRepository;

    /**
     * 강의 목록 페이징 조회 (카테고리 필터링 선택 가능)
     */
    public LecturePageResponse getLectures(String email, String categoryName, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new LectureException(ErrorStatus.DELETED_MEMBER));

        Page<Lecture> lecturePage;
        long completedCount;

        // 카테고리 조건 분기
        if (categoryName == null || categoryName.isBlank() || "ALL".equalsIgnoreCase(categoryName)) {
            lecturePage = lectureRepository.findAllByOrderByIdAsc(pageable);
            completedCount = progressRepository.countCompletedByUserId(user.getId());
        } else {
            try {
                Category category = Category.valueOf(categoryName.toUpperCase());
                lecturePage = lectureRepository.findByCategoryOrderByIdAsc(category, pageable);
                completedCount = progressRepository.countCompletedByUserIdAndCategory(user.getId(), category);
            } catch (IllegalArgumentException e) {
                // 지원하지 않는 카테고리 예외 (LECTURE400)
                throw new LectureException(ErrorStatus.INVALID_CATEGORY);
            }
        }

        // 유저가 완료한 강의 ID 목록 조회 (N+1 문제 방지)
        Set<Long> completedLectureIds = new HashSet<>(
                progressRepository.findCompletedLectureIdsByUserId(user.getId())
        );

        // Page<Lecture> -> Page<LectureResponse> 변환
        Page<LectureResponse> responsePage = lecturePage.map(lecture -> {
            boolean isCompleted = completedLectureIds.contains(lecture.getId());
            return LectureResponse.of(lecture, isCompleted);
        });

        return LecturePageResponse.of(responsePage, completedCount);
    }

    /**
     * 단건 강의 상세 조회
     */
    public LectureResponse getLectureDetail(String email, Long lectureId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new LectureException(ErrorStatus.DELETED_MEMBER));

        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureException(ErrorStatus.LECTURE_NOT_FOUND));

        boolean isCompleted = progressRepository
                .existsByUser_idAndLecture_idAndIsCompletedTrue(user.getId(), lectureId);

        return LectureResponse.of(lecture, isCompleted);
    }

    /**
     * 강의 수강 진도 및 완료 상태 업데이트
     */
    @Transactional
    public LectureProgressResponse completeLecture(String email, Long lectureId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new LectureException(ErrorStatus.DELETED_MEMBER));

        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureException(ErrorStatus.LECTURE_NOT_FOUND));

        // 기존 진도 기록이 없으면 생성, 있으면 조회
        UserLectureProgress progress = progressRepository
                .findByUser_idAndLecture_id(user.getId(), lectureId)
                .orElseGet(() -> UserLectureProgress.createProgress(user, lecture));

        // 완료 상태 업데이트 및 시간 갱신
        progress.complete();

        UserLectureProgress savedProgress = progressRepository.save(progress);

        return LectureProgressResponse.from(savedProgress);
    }

    /**
     * 카테고리 내 완강한 강의 개수 조회 (예: GREETING 카테고리에서 몇 개 완료했는지)
     */
    @Transactional(readOnly = true)
    public long getCompletedCountByCategory(Long userId, Lecture.Category category) {
        return progressRepository.countCompletedByUserIdAndCategory(userId, category);
    }
}