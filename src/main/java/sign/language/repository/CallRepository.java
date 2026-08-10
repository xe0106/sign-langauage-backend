package sign.language.repository;

/**
 * 통화 세션 리포지토리 인터페이스
 * 
 * Spring Data JPA 사용 시 JpaRepository를 상속받아 사용합니다.
 * 예시: public interface CallRepository extends JpaRepository<CallSession, String> {}
 */
public interface CallRepository {
    // 필요한 DB 조회/저장 커스텀 메서드 정의 위치
}
