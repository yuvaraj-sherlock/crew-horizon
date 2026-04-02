package com.crewhorizon.authservice.repository;

import com.crewhorizon.authservice.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ============================================================
 * User Repository
 * ============================================================
 * WHAT: Spring Data JPA repository for User entity persistence
 *       operations. Extends JpaRepository to inherit CRUD
 *       operations automatically.
 *
 * WHY Spring Data JPA (not manual DAO):
 *       Spring Data generates query implementations at runtime
 *       from method names and @Query annotations, eliminating
 *       boilerplate JDBC/Hibernate code. This reduces errors
 *       and accelerates development while maintaining full
 *       control over complex queries via JPQL/@Query.
 *
 * WHY @Repository annotation:
 *       While Spring Data repos are auto-detected, @Repository
 *       enables Spring's exception translation mechanism —
 *       converting DB-specific exceptions (SQLIntegrityConstraint)
 *       into Spring's DataAccessException hierarchy. This
 *       decouples the service layer from DB-specific exceptions.
 *
 * WHY isDeletedFalse in query methods:
 *       Implements the soft-delete pattern transparently.
 *       Business logic never sees "deleted" users without
 *       explicit opt-in, maintaining data isolation.
 * ============================================================
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * Primary lookup for authentication.
     *
     * WHY Optional<UserEntity>:
     * Avoids null pointer exceptions. The caller MUST handle
     * the "not found" case explicitly using .orElseThrow()
     * or .orElse(), preventing silent NullPointerExceptions.
     */
    Optional<UserEntity> findByEmailAndIsDeletedFalse(String email);

    Optional<UserEntity> findByEmployeeIdAndIsDeletedFalse(String employeeId);

    boolean existsByEmail(String email);
    boolean existsByEmployeeId(String employeeId);

    /**
     * WHY @Modifying + @Query instead of entity save for these updates:
     * Loading the full entity, modifying, and saving back is wasteful
     * for simple column updates. @Query generates a targeted UPDATE
     * statement — lower latency, reduced memory usage, fewer DB round-trips.
     *
     * WHY @Modifying(clearAutomatically = true):
     * After a bulk/direct update, the first-level cache (persistence context)
     * is stale. clearAutomatically flushes it so subsequent finds return
     * fresh data. Without this, you'd get cached (pre-update) data.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.failedLoginAttempts = u.failedLoginAttempts + 1, " +
           "u.lockedUntil = CASE WHEN u.failedLoginAttempts + 1 >= 5 " +
           "THEN :lockUntil ELSE u.lockedUntil END WHERE u.email = :email")
    void incrementFailedLoginAttempts(@Param("email") String email,
                                      @Param("lockUntil") LocalDateTime lockUntil);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.failedLoginAttempts = 0, u.lockedUntil = null, " +
           "u.lastLoginAt = :loginTime WHERE u.email = :email")
    void resetFailedAttemptsAndUpdateLastLogin(@Param("email") String email,
                                               @Param("loginTime") LocalDateTime loginTime);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.isDeleted = true, u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.id = :id")
    void softDeleteById(@Param("id") Long id);
}
