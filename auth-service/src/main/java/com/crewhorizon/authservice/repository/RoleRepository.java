package com.crewhorizon.authservice.repository;

import com.crewhorizon.authservice.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Role Repository.
 * WHY: Roles are reference data — small set, rarely changed.
 * Pre-populated during DB initialization (Flyway migration).
 */
@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByName(RoleEntity.RoleName name);
}
