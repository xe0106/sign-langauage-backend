package sign.language.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sign.language.domain.CallSubtitle;

import java.util.List;

@Repository
public interface SubtitleRepository extends JpaRepository<CallSubtitle, Long> {

    // 특정 통화 세션(callId)에 속한 모든 자막 생성시간 순으로 조회
    List<CallSubtitle> findByCall_CallIdOrderByCreatedAtAsc(String callId);
}