-- ============================================================
-- Crew Service — Flyway Migration V1: Initial Schema
-- ============================================================
CREATE TABLE IF NOT EXISTS crew_members (
    id                          BIGSERIAL PRIMARY KEY,
    employee_id                 VARCHAR(20)  NOT NULL UNIQUE,
    first_name                  VARCHAR(100) NOT NULL,
    last_name                   VARCHAR(100) NOT NULL,
    email                       VARCHAR(255) NOT NULL UNIQUE,
    crew_type                   VARCHAR(30)  NOT NULL,
    duty_status                 VARCHAR(30)  NOT NULL DEFAULT 'AVAILABLE',
    base_airport                VARCHAR(3)   NOT NULL,
    seniority_number            INTEGER,
    date_of_birth               DATE,
    date_of_hire                DATE         NOT NULL,
    license_number              VARCHAR(50),
    license_expiry              DATE,
    medical_certificate_expiry  DATE,
    nationality                 VARCHAR(3),
    passport_number             VARCHAR(20),
    passport_expiry             DATE,
    is_active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                  VARCHAR(100),
    updated_by                  VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS crew_qualifications (
    id                  BIGSERIAL PRIMARY KEY,
    crew_member_id      BIGINT       NOT NULL REFERENCES crew_members(id) ON DELETE CASCADE,
    aircraft_type       VARCHAR(20)  NOT NULL,
    qualification_type  VARCHAR(30)  NOT NULL,
    issued_date         DATE         NOT NULL,
    expiry_date         DATE,
    issuing_authority   VARCHAR(100),
    certificate_number  VARCHAR(50),
    is_current          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Performance indexes based on common query patterns
CREATE INDEX IF NOT EXISTS idx_crew_employee_id    ON crew_members(employee_id);
CREATE INDEX IF NOT EXISTS idx_crew_type           ON crew_members(crew_type);
CREATE INDEX IF NOT EXISTS idx_crew_base_airport   ON crew_members(base_airport);
CREATE INDEX IF NOT EXISTS idx_crew_duty_status    ON crew_members(duty_status);
-- Composite index for the most common scheduling query: available crew at an airport
CREATE INDEX IF NOT EXISTS idx_crew_base_status    ON crew_members(base_airport, duty_status) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_qual_crew_member    ON crew_qualifications(crew_member_id);
CREATE INDEX IF NOT EXISTS idx_qual_aircraft_type  ON crew_qualifications(aircraft_type);
-- Index for expiry alerting query
CREATE INDEX IF NOT EXISTS idx_qual_expiry         ON crew_qualifications(expiry_date) WHERE is_current = TRUE;

-- Updated_at trigger (same pattern as auth-service)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;
$$ language 'plpgsql';

CREATE TRIGGER update_crew_members_updated_at
    BEFORE UPDATE ON crew_members
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
