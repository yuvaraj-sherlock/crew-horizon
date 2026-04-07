-- ============================================================
-- Flight Service — Flyway Migration V1: Initial Schema
-- ============================================================
CREATE TABLE IF NOT EXISTS flights (
    id                      BIGSERIAL PRIMARY KEY,
    flight_number           VARCHAR(10)  NOT NULL,
    departure_airport       VARCHAR(3)   NOT NULL,
    arrival_airport         VARCHAR(3)   NOT NULL,
    scheduled_departure     TIMESTAMPTZ  NOT NULL,
    scheduled_arrival       TIMESTAMPTZ  NOT NULL,
    actual_departure        TIMESTAMPTZ,
    actual_arrival          TIMESTAMPTZ,
    aircraft_type           VARCHAR(20)  NOT NULL,
    aircraft_registration   VARCHAR(10),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    required_pilots         INTEGER      NOT NULL DEFAULT 2,
    required_cabin_crew     INTEGER      NOT NULL DEFAULT 4,
    planned_duration_minutes INTEGER,
    delay_minutes           INTEGER      NOT NULL DEFAULT 0,
    delay_reason            VARCHAR(255),
    is_cancelled            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_flight_number_date   ON flights(flight_number, scheduled_departure);
CREATE INDEX IF NOT EXISTS idx_flight_departure     ON flights(departure_airport);
CREATE INDEX IF NOT EXISTS idx_flight_arrival       ON flights(arrival_airport);
CREATE INDEX IF NOT EXISTS idx_flight_status        ON flights(status);
CREATE INDEX IF NOT EXISTS idx_flight_aircraft_type ON flights(aircraft_type);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$ BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END; $$ language 'plpgsql';
CREATE TRIGGER update_flights_updated_at BEFORE UPDATE ON flights
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
