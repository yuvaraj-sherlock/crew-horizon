-- ============================================================
-- Roster Service — Flyway Migration V1: Initial Schema
-- ============================================================
CREATE TABLE IF NOT EXISTS roster_assignments (
    id                  BIGSERIAL PRIMARY KEY,
    employee_id         VARCHAR(20)  NOT NULL,
    flight_number       VARCHAR(10)  NOT NULL,
    assigned_role       VARCHAR(30)  NOT NULL,
    duty_date           DATE         NOT NULL,
    report_time         TIMESTAMPTZ,
    duty_start_time     TIMESTAMPTZ,
    duty_end_time       TIMESTAMPTZ,
    flight_time_minutes INTEGER,
    total_duty_minutes  INTEGER,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    departure_airport   VARCHAR(3),
    arrival_airport     VARCHAR(3),
    notes               VARCHAR(500),
    is_ftl_compliant    BOOLEAN      NOT NULL DEFAULT TRUE,
    ftl_violation_reason VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by         VARCHAR(100),
    CONSTRAINT uk_roster_employee_flight UNIQUE (employee_id, flight_number, duty_date)
);

CREATE INDEX IF NOT EXISTS idx_roster_employee_id  ON roster_assignments(employee_id);
CREATE INDEX IF NOT EXISTS idx_roster_flight_number ON roster_assignments(flight_number);
CREATE INDEX IF NOT EXISTS idx_roster_duty_date    ON roster_assignments(duty_date);
CREATE INDEX IF NOT EXISTS idx_roster_status       ON roster_assignments(status);
CREATE INDEX IF NOT EXISTS idx_roster_emp_date     ON roster_assignments(employee_id, duty_date);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$ BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END; $$ language 'plpgsql';
CREATE TRIGGER update_roster_updated_at BEFORE UPDATE ON roster_assignments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
