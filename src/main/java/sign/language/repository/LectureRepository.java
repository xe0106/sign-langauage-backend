package sign.language.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sign.language.domain.Lecture;
import sign.language.domain.Lecture.Category;

import java.util.List;

public interface LectureRepository extends JpaRepository<Lecture, Long> {

    // 전체 강의 페이징 조회
    Page<Lecture> findAllByOrderByIdAsc(Pageable pageable);

    // 카테고리별 강의 페이징 조회
    Page<Lecture> findByCategoryOrderByIdAsc(Category category, Pageable pageable);

    // 무작위로 Lecture N개 조회 (퀴즈 생성용)
    @Query("SELECT l FROM Lecture l ORDER BY FUNCTION('RAND')")
    List<Lecture> findRandomLectures(Pageable pageable);

    // 지정한 ID 목록을 제외한 무작위 Lecture N개 조회 (오답지 생성용)
    @Query("SELECT l FROM Lecture l WHERE l.id NOT IN :excludedIds ORDER BY FUNCTION('RAND')")
    List<Lecture> findRandomLecturesExcluding(@Param("excludedIds") List<Long> excludedIds, Pageable pageable);
}