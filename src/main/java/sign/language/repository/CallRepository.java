package sign.language.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sign.language.domain.CallSession;

/**
 * 통화 세션 리포지토리 인터페이스
 * 
 * Spring Data JPA 사용 시 JpaRepository를 상속받아 사용합니다.
 * 예시: public interface CallRepository extends JpaRepository<CallSession, String> {}
 */
public interface CallRepository extends JpaRepository<CallSession, String> {
    // 필요한 DB 조회/저장 커스텀 메서드 정의 위치
}
