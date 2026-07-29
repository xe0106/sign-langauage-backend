package sign.language.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sign.language.domain.Contact;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    // 모든 연락처 조회 (이름 오름차순 정렬)
    @Query("SELECT c FROM Contact c LEFT JOIN FETCH c.targetUser WHERE c.user.id = :userId ORDER BY c.contactName ASC")
    List<Contact> findAllByUserIdWithTargetUser(@Param("userId") Long userId);

    // 최근 연락처 조회 (최근 연락 시각 기준 내림차순, lastContactedAt이 null이면 createdAt 기준)
    @Query("SELECT c FROM Contact c LEFT JOIN FETCH c.targetUser WHERE c.user.id = :userId " +
            "ORDER BY COALESCE(c.lastContactedAt, c.createdAt) DESC")
    List<Contact> findRecentContactsByUserId(@Param("userId") Long userId, Pageable pageable);

    // 이미 등록된 연락처인지 확인 (중복 등록 방지용)
    boolean existsByUserIdAndTargetUserId(Long userId, Long targetUserId);

    // 로그인한 유저(userId)가 등록한 특정 상대방(targetUserId)의 연락처 조회
    Optional<Contact> findByUserIdAndTargetUserId(Long userId, Long targetUserId);
}