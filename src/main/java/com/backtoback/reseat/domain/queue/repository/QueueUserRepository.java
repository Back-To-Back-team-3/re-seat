package com.backtoback.reseat.domain.queue.repository;

import com.backtoback.reseat.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 동일 사용자의 대기열 등록과 취소를 순서대로 처리하기 위해 사용자 행을 비관적 락으로 조회하는 Repository
 */
public interface QueueUserRepository extends JpaRepository<User, Long> {

    /**
     * 동일 사용자의 대기열 등록과 취소를 순서대로 처리하기 위해 사용자 행을 비관적 락으로 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 조회된 사용자
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u
        FROM User u
        WHERE u.id = :userId
        """)
    Optional<User> findByIdWithPessimisticWriteLock(@Param("userId") Long userId);
}
