package com.crewhorizon.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * ============================================================
 * User Entity
 * ============================================================
 * WHAT: JPA entity representing a system user (crew member,
 *       scheduler, operations staff, admin).
 *
 * WHY @Entity with proper constraints:
 *       Database-level constraints (unique, nullable=false) are
 *       the LAST line of defense against bad data. Application-
 *       level validation (@Valid) can be bypassed; DB constraints
 *       cannot. Both layers are required in production systems.
 *
 * WHY @ManyToMany with EAGER fetch for roles:
 *       Roles are needed every time a user is loaded (for JWT
 *       generation and authorization checks). EAGER fetch here
 *       avoids the "LazyInitializationException" problem outside
 *       transaction boundaries. Given roles are small in number,
 *       EAGER is acceptable (unlike large collections).
 *
 * WHY soft delete (isDeleted flag) instead of hard delete:
 *       Aviation systems require audit trails. Users must never
 *       truly be deleted — their historical assignments, duty
 *       logs, and audit records must remain traceable.
 *       Soft delete preserves referential integrity.
 *
 * WHY BaseEntity is not used here (self-contained):
 *       The User entity contains audit fields directly to avoid
 *       superclass complexities in a security-critical entity.
 * ============================================================
 */
@Entity
@Table(
        name = "users",
        indexes = {
                // WHY index on email: Primary lookup key for authentication
                @Index(name = "idx_users_email", columnList = "email"),
                // WHY index on employeeId: Secondary lookup for crew system integration
                @Index(name = "idx_users_employee_id", columnList = "employee_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * WHY employeeId as a separate field:
     * The airline's HR system uses employee IDs, not email addresses.
     * This field bridges the gap between the auth system and the
     * crew management system without exposing internal DB IDs.
     */
    @Column(name = "employee_id", unique = true, nullable = false, length = 20)
    private String employeeId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * WHY BCrypt hashed password stored here (not plain text):
     * BCrypt is an adaptive one-way hash function:
     * - Salted: prevents rainbow table attacks
     * - Adaptive work factor: slowness is a feature (slows brute force)
     * - Standard: supported across all Spring Security versions
     *
     * The plain text password NEVER touches the database or logs.
     */
    @Column(nullable = false)
    private String password;

    /**
     * WHY ManyToMany with a join table:
     * A user can have multiple roles (PILOT + SENIOR_CREW_MEMBER)
     * and a role can be assigned to multiple users.
     * The junction table avoids data duplication.
     *
     * WHY EAGER fetch:
     * Spring Security's loadUserByUsername() needs roles immediately
     * to build the UserDetails object. Lazy loading would require
     * a transaction still being open at that point.
     */
    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<RoleEntity> roles = new HashSet<>();

    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    @Column(name = "is_account_non_expired")
    @Builder.Default
    private Boolean isAccountNonExpired = true;

    @Column(name = "is_account_non_locked")
    @Builder.Default
    private Boolean isAccountNonLocked = true;

    @Column(name = "is_credentials_non_expired")
    @Builder.Default
    private Boolean isCredentialsNonExpired = true;

    /**
     * WHY track failed login attempts at DB level:
     * Brute force protection requires persistence across
     * service restarts. In-memory counters would reset on
     * pod restarts in Kubernetes.
     */
    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * WHY soft delete:
     * IATA regulations require historical records of all crew
     * assignments. Hard deleting a user would violate referential
     * integrity and audit trail requirements.
     */
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    // Audit timestamps — auto-populated by Hibernate
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    // Helper method: check if account is currently locked
    public boolean isCurrentlyLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    // Helper method: add a role
    public void addRole(RoleEntity role) {
        this.roles.add(role);
    }
}
