package com.crewhorizon.authservice.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * ============================================================
 * Role Entity - RBAC Foundation
 * ============================================================
 * WHAT: Represents a system role for Role-Based Access Control.
 *       Defines the set of permissions a user group holds.
 *
 * WHY RBAC (Role-Based Access Control) over ABAC or ACL:
 *       In airline operations, access patterns are well-defined
 *       and role-centric:
 *       - PILOT: Can view own roster, cannot modify crew assignments
 *       - CREW_SCHEDULER: Can create/modify rosters
 *       - OPERATIONS: Read-only flight and crew data
 *       - ADMIN: Full system access
 *
 *       RBAC maps naturally to these job functions:
 *       - Simple to understand and audit
 *       - Manageable number of roles (vs individual ACLs)
 *       - Spring Security's @PreAuthorize integrates natively
 *
 * WHY RoleName Enum:
 *       String-based role names are error-prone (typos, case
 *       mismatches). An enum provides compile-time safety and
 *       enables IDE autocompletion.
 * ============================================================
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * WHY @Enumerated(STRING) over ORDINAL:
     * If new roles are inserted between existing enum values,
     * ORDINAL would shift all ordinal values — corrupting the DB.
     * STRING is stable regardless of enum ordering changes.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private RoleName name;

    @Column(length = 255)
    private String description;

    /**
     * Role names for CREW Horizon.
     * WHY "ROLE_" prefix: Spring Security's hasRole() method
     * expects this prefix. Without it, you must use hasAuthority().
     */
    public enum RoleName {
        ROLE_ADMIN,           // Full system access — IT administrators
        ROLE_CREW_SCHEDULER,  // Create/modify rosters and assignments
        ROLE_OPERATIONS,      // Read-only operational data view
        ROLE_PILOT,           // Own roster + flight info (read-only)
        ROLE_CABIN_CREW,      // Own roster + basic flight info
        ROLE_HR_MANAGER,      // Crew profile management + qualifications
        ROLE_COMPLIANCE       // Read-only + audit report generation
    }
}
