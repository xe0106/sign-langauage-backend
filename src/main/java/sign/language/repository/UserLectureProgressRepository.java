package sign.language.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sign.language.domain.Lecture;
import sign.language.domain.UserLectureProgress;

import java.util.List;
import java.util.Optional;

public interface UserLectureProgressRepository extends JpaRepository<UserLectureProgress, Long> {

    // 특정 유저와 특정 강의의 수강 기록 조회 (User의 userId, Lecture의 lectureId 참조)
    Optional<UserLectureProgress> findByUser_idAndLecture_id(Long userId, Long lectureId);

    // 단건 강의 수강 완료 여부 확인
    boolean existsByUser_idAndLecture_idAndIsCompletedTrue(Long userId, Long lectureId);

    // N+1 방지: 유저가 수강 완료한 강의 ID 목록 조회 (Entity 필드명 p.user.userId, p.lecture.lectureId, p.isCompleted 에 매칭)
    @Query("SELECT p.lecture.id FROM UserLectureProgress p WHERE p.user.id = :userId AND p.isCompleted = true")
    List<Long> findCompletedLectureIdsByUserId(@Param("userId") Long userId);

    // 카테고리별 완강 수 집계
    @Query("SELECT COUNT(p) FROM UserLectureProgress p WHERE p.user.id = :userId AND p.lecture.category = :category AND p.isCompleted = true")
    long countCompletedByUserIdAndCategory(@Param("userId") Long userId, @Param("category") Lecture.Category category);

    // 전체 완강 수 집계 (카테고리 구분 없이 유저가 완료한 모든 강의 수)
    @Query("SELECT COUNT(p) FROM UserLectureProgress p WHERE p.user.id = :userId AND p.isCompleted = true")
    long countCompletedByUserId(@Param("userId") Long userId);
}