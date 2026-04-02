package com.crewhorizon.crewservice.repository;

import com.crewhorizon.crewservice.entity.CrewMemberEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * Crew Member Repository
 * ============================================================
 * WHAT: Data access layer for CrewMemberEntity with support
 *       for both simple CRUD and complex dynamic queries.
 *
 * WHY JpaSpecificationExecutor:
 *       The crew scheduling dashboard has complex filter
 *       requirements: filter by crew type AND base airport
 *       AND duty status AND aircraft qualification.
 *       Building 2^N query methods for all combinations is
 *       unsustainable. JpaSpecificationExecutor enables
 *       dynamic query composition via the Specification pattern
 *       (similar to SQL WHERE clause building).
 *
 * WHY Pageable parameters:
 *       Airlines can have thousands of crew members.
 *       Loading all records at once would:
 *       - Exhaust JVM heap memory
 *       - Create slow API responses
 *       - Transfer unnecessary data over the network
 *       Pageable enables server-side pagination —
 *       only the requested page is loaded from DB.
 * ============================================================
 */
@Repository
public interface CrewMemberRepository
        extends JpaRepository<CrewMemberEntity, Long>,
                JpaSpecificationExecutor<CrewMemberEntity> {

    Optional<CrewMemberEntity> findByEmployeeIdAndIsDeletedFalse(String employeeId);

    Optional<CrewMemberEntity> findByEmailAndIsDeletedFalse(String email);

    boolean existsByEmployeeId(String employeeId);
    boolean existsByEmail(String email);

    /**
     * WHY @Query with JPQL (not native SQL):
     * JPQL operates on entity objects, not DB tables.
     * It's database-agnostic — same query works on PostgreSQL,
     * MySQL, or H2 (tests). Native SQL would break on DB changes.
     *
     * WHY countQuery parameter:
     * Spring Data needs a separate COUNT query for pagination.
     * Without it, Spring generates a COUNT query that includes
     * all JOINs — extremely slow for large datasets.
     * Providing a lean COUNT query dramatically improves
     * pagination performance.
     */
    @Query(value = """
            SELECT c FROM CrewMemberEntity c
            WHERE c.isDeleted = false
            AND (:crewType IS NULL OR c.crewType = :crewType)
            AND (:baseAirport IS NULL OR c.baseAirport = :baseAirport)
            AND (:dutyStatus IS NULL OR c.dutyStatus = :dutyStatus)
            """,
            countQuery = """
            SELECT COUNT(c) FROM CrewMemberEntity c
            WHERE c.isDeleted = false
            AND (:crewType IS NULL OR c.crewType = :crewType)
            AND (:baseAirport IS NULL OR c.baseAirport = :baseAirport)
            AND (:dutyStatus IS NULL OR c.dutyStatus = :dutyStatus)
            """)
    Page<CrewMemberEntity> findByFilters(
            @Param("crewType") CrewMemberEntity.CrewType crewType,
            @Param("baseAirport") String baseAirport,
            @Param("dutyStatus") CrewMemberEntity.DutyStatus dutyStatus,
            Pageable pageable);

    /**
     * Find available crew qualified for a specific aircraft type.
     * WHY JOIN with qualifications here:
     * This query is the CORE of crew scheduling — finding who
     * CAN fly a specific aircraft AND is AVAILABLE.
     * The JOIN is necessary and justified by the use case.
     */
    @Query("""
            SELECT DISTINCT c FROM CrewMemberEntity c
            JOIN c.qualifications q
            WHERE c.isDeleted = false
            AND c.isActive = true
            AND c.dutyStatus = 'AVAILABLE'
            AND q.aircraftType = :aircraftType
            AND q.isCurrent = true
            AND (q.expiryDate IS NULL OR q.expiryDate > CURRENT_DATE)
            AND (:baseAirport IS NULL OR c.baseAirport = :baseAirport)
            ORDER BY c.seniorityNumber ASC
            """)
    List<CrewMemberEntity> findAvailableCrewByAircraftType(
            @Param("aircraftType") String aircraftType,
            @Param("baseAirport") String baseAirport);

    /**
     * Crew members with expiring qualifications (compliance alerts).
     * WHY: Proactive alerts prevent scheduling crew with expired
     * qualifications — an ICAO regulatory violation.
     */
    @Query("""
            SELECT DISTINCT c FROM CrewMemberEntity c
            JOIN c.qualifications q
            WHERE c.isDeleted = false
            AND q.isCurrent = true
            AND q.expiryDate BETWEEN CURRENT_DATE AND :daysAhead
            ORDER BY q.expiryDate ASC
            """)
    List<CrewMemberEntity> findCrewWithExpiringQualifications(
            @Param("daysAhead") java.time.LocalDate daysAhead);

    Page<CrewMemberEntity> findAllByIsDeletedFalse(Pageable pageable);
}
