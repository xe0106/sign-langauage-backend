package sign.language.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sign.language.domain.Quiz;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    // 퀴즈 무작위 N개 추출
    @Query("SELECT q FROM Quiz q ORDER BY FUNCTION('RAND')")
    List<Quiz> findRandomQuizzes(Pageable pageable);
}