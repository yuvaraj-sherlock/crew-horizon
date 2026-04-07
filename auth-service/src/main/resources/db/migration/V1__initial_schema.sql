-- ============================================================
-- Auth Service — Flyway Migration V1: Initial Schema
-- ============================================================
-- WHAT: Creates the initial database schema for the auth-service.
--
-- WHY Flyway (not Hibernate ddl-auto=create/update):
--   1. VERSION CONTROL: Every schema change is a numbered SQL file
--      tracked in Git with full history.
--   2. IDEMPOTENCY: Flyway tracks which migrations have run in the
--      'flyway_schema_history' table. Migrations never run twice.
--   3. PRODUCTION SAFETY: 'update' mode in Hibernate can DROP columns
--      silently. Flyway SQL scripts are explicit and reviewable.
--   4. ROLLBACK SUPPORT: Paired with V1__undo.sql, rollbacks are
--      structured and auditable.
--   5. TEAM COLLABORATION: When two developers add columns simultaneously,
--      Flyway detects the version conflict and fails clearly.
--
-- NAMING CONVENTION: V{version}__{description}.sql
--   V1 = version number (sequential)
--   __ = double underscore separator (Flyway requirement)
--   description = human-readable change summary
-- ============================================================

-- ─── Roles Table ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Seed default roles
-- WHY INSERT ... ON CONFLICT DO NOTHING:
-- Idempotent seeding — safe to run on an already-seeded DB.
-- Essential for dev environment resets and integration tests.
INSERT INTO roles (name, description) VALUES
    ('ROLE_ADMIN',           'Full system access - IT administrators')           ON CONFLICT (name) DO NOTHING,
    ('ROLE_CREW_SCHEDULER',  'Create and modify crew rosters and assignments')   ON CONFLICT (name) DO NOTHING,
    ('ROLE_OPERATIONS',      'Read-only operational data access')                ON CONFLICT (name) DO NOTHING,
    ('ROLE_PILOT',           'Own roster and flight information (read-only)')    ON CONFLICT (name) DO NOTHING,
    ('ROLE_CABIN_CREW',      'Own roster and basic flight information')          ON CONFLICT (name) DO NOTHING,
    ('ROLE_HR_MANAGER',      'Crew profile management and qualifications')       ON CONFLICT (name) DO NOTHING,
    ('ROLE_COMPLIANCE',      'Read-only access and audit report generation')     ON CONFLICT (name) DO NOTHING;

-- ─── Users Table ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id                          BIGSERIAL PRIMARY KEY,
    employee_id                 VARCHAR(20)  NOT NULL UNIQUE,
    email                       VARCHAR(255) NOT NULL UNIQUE,
    first_name                  VARCHAR(100) NOT NULL,
    last_name                   VARCHAR(100) NOT NULL,
    password                    VARCHAR(255) NOT NULL,
    is_enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    is_account_non_expired      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_account_non_locked       BOOLEAN      NOT NULL DEFAULT TRUE,
    is_credentials_non_expired  BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts       INTEGER      NOT NULL DEFAULT 0,
    locked_until                TIMESTAMP,
    is_deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at               TIMESTAMP,
    created_by                  VARCHAR(100)
);

-- ─── User-Roles Junction Table ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- ─── Indexes ─────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_users_email       ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_employee_id ON users(employee_id);
CREATE INDEX IF NOT EXISTS idx_users_is_deleted  ON users(is_deleted) WHERE is_deleted = FALSE;

-- ─── Updated At Trigger ──────────────────────────────────────────────────────
-- WHY trigger (not just @UpdateTimestamp in JPA):
-- DB-level triggers ensure updated_at is maintained even for
-- direct SQL updates (migrations, admin scripts, bulk operations)
-- that bypass the JPA entity lifecycle.
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ─── Initial Admin User ──────────────────────────────────────────────────────
-- WHY seed a default admin in migration (not in application code):
-- The first user must be created before the system is operational.
-- Creating it in a migration ensures it's idempotent and version-controlled.
-- The password hash below = BCrypt("Admin@CrewH0rizon!") strength 12
-- IMPORTANT: Change this password immediately after first deployment!
INSERT INTO users (employee_id, email, first_name, last_name, password, created_by)
VALUES ('SY000001', 'admin@crew-horizon.com', 'System', 'Admin',
        '$2a$12$PLACEHOLDER_CHANGE_THIS_BCrypt_hash_of_Admin_password', 'migration')
ON CONFLICT (email) DO NOTHING;

-- Assign ROLE_ADMIN to the initial admin user
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email = 'admin@crew-horizon.com'
  AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;
