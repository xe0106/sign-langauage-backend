package sign.language.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import sign.language.domain.Lecture;
import sign.language.domain.Lecture.Category;

public interface LectureRepository extends JpaRepository<Lecture, Long> {

    // 전체 강의 페이징 조회
    Page<Lecture> findAll(Pageable pageable);

    // 카테고리별 강의 페이징 조회
    Page<Lecture> findByCategory(Category category, Pageable pageable);
}