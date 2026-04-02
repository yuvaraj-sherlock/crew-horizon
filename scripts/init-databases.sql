-- ============================================================
-- CREW HORIZON - Database Initialization Script
-- ============================================================
-- WHAT: Creates separate databases for each microservice in
--       the local development PostgreSQL instance.
--
-- WHY Database-per-Service pattern:
--       Each microservice has its own database schema.
--       This enforces data isolation at the database level:
--
--       1. LOOSE COUPLING: crew-service cannot directly JOIN
--          against the auth DB tables — must use the API.
--          This prevents tight coupling through shared DB.
--
--       2. INDEPENDENT EVOLUTION: auth DB schema can change
--          without impacting crew DB or vice versa.
--
--       3. INDEPENDENT SCALING: Each DB can be sized and
--          scaled based on its specific workload. The roster DB
--          might need more CPU (complex queries), while the
--          notification DB needs more storage (log retention).
--
--       4. TECHNOLOGY FLEXIBILITY: In principle, each service
--          could use a different DB technology (PostgreSQL,
--          MongoDB, Cassandra) based on its data access patterns.
-- ============================================================

-- Auth Service Database
CREATE DATABASE auth_db;
GRANT ALL PRIVILEGES ON DATABASE auth_db TO crew_horizon_user;

-- Crew Service Database
CREATE DATABASE crew_db;
GRANT ALL PRIVILEGES ON DATABASE crew_db TO crew_horizon_user;

-- Flight Service Database
CREATE DATABASE flight_db;
GRANT ALL PRIVILEGES ON DATABASE flight_db TO crew_horizon_user;

-- Roster Service Database
CREATE DATABASE roster_db;
GRANT ALL PRIVILEGES ON DATABASE roster_db TO crew_horizon_user;

-- Notification Service Database
CREATE DATABASE notification_db;
GRANT ALL PRIVILEGES ON DATABASE notification_db TO crew_horizon_user;

-- Log the initialization
DO $$
BEGIN
    RAISE NOTICE 'CREW Horizon databases initialized successfully';
END $$;
