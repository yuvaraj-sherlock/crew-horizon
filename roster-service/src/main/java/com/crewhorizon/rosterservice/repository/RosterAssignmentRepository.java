package com.crewhorizon.rosterservice.repository;

import com.crewhorizon.rosterservice.entity.RosterAssignmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * Roster Assignment Repository
 * ============================================================
 * WHAT: Data access layer for roster assignments with specialized
 *       queries for FTL compliance checks and scheduling views.
 *
 * WHY complex JPQL queries here (not service layer):
 *       These queries are data-retrieval operations that belong
 *       in the data layer. Placing DB query logic in the service
 *       layer (via streams on loaded collections) would require
 *       loading ALL assignments into memory — catastrophic at scale.
 *       JPQL aggregate queries (SUM, COUNT) run in the DB where
 *       they're most efficient.
 * ============================================================
 */
@Repository
public interface RosterAssignmentRepository extends JpaRepository<RosterAssignmentEntity, Long> {

    /**
     * Core FTL query: find recent completed assignments to check rest period.
     *
     * WHY order by dutyDate DESC + limit via Pageable:
     * We only need the LAST completed assignment. Using findTop1
     * generates "LIMIT 1" in SQL — efficient regardless of history size.
     */
    @Query("""
            SELECT r FROM RosterAssignmentEntity r
            WHERE r.employeeId = :employeeId
            AND r.status IN ('COMPLETED', 'ACTIVE')
            AND r.dutyDate <= :before
            ORDER BY r.dutyDate DESC, r.dutyEndTime DESC
            """)
    List<RosterAssignmentEntity> findLastCompletedAssignment(
            @Param("employeeId") String employeeId,
            @Param("before") LocalDate before);

    /**
     * FTL compliance query: total flight minutes in a date range.
     *
     * WHY SUM in DB (not in memory):
     * An airline crew member could have 200+ assignment records.
     * Loading all to sum in Java would be wasteful. SUM in SQL
     * returns a single number — O(1) memory regardless of data size.
     *
     * WHY COALESCE(SUM(...), 0):
     * If no records exist in the range, SUM returns NULL (not 0).
     * The COALESCE handles this, avoiding NullPointerException
     * when converting to int.
     */
    @Query("""
            SELECT COALESCE(SUM(r.flightTimeMinutes), 0)
            FROM RosterAssignmentEntity r
            WHERE r.employeeId = :employeeId
            AND r.dutyDate BETWEEN :from AND :to
            AND r.status NOT IN ('CANCELLED', 'SWAPPED')
            """)
    Optional<Integer> sumFlightMinutesBetween(
            @Param("employeeId") String employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * FTL compliance query: total duty minutes in a date range.
     */
    @Query("""
            SELECT COALESCE(SUM(r.totalDutyMinutes), 0)
            FROM RosterAssignmentEntity r
            WHERE r.employeeId = :employeeId
            AND r.dutyDate BETWEEN :from AND :to
            AND r.status NOT IN ('CANCELLED', 'SWAPPED')
            """)
    Optional<Integer> sumDutyMinutesBetween(
            @Param("employeeId") String employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Crew schedule view: all assignments for a crew member in a month.
     * Used by the crew portal (pilots viewing their own roster).
     */
    @Query("""
            SELECT r FROM RosterAssignmentEntity r
            WHERE r.employeeId = :employeeId
            AND r.dutyDate BETWEEN :from AND :to
            ORDER BY r.dutyDate ASC, r.reportTime ASC
            """)
    List<RosterAssignmentEntity> findByEmployeeIdAndDateRange(
            @Param("employeeId") String employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Flight crew manifest: all crew assigned to a specific flight.
     * Used by operations to view complete crew complement.
     */
    @Query("""
            SELECT r FROM RosterAssignmentEntity r
            WHERE r.flightNumber = :flightNumber
            AND r.dutyDate = :dutyDate
            AND r.status NOT IN ('CANCELLED', 'SWAPPED')
            ORDER BY r.assignedRole ASC
            """)
    List<RosterAssignmentEntity> findCrewForFlight(
            @Param("flightNumber") String flightNumber,
            @Param("dutyDate") LocalDate dutyDate);

    /**
     * Admin view: paginated roster with date range filter.
     * WHY Page<> return type: Roster admin views can show thousands
     * of assignments. Server-side pagination is essential.
     */
    @Query(value = """
            SELECT r FROM RosterAssignmentEntity r
            WHERE (:employeeId IS NULL OR r.employeeId = :employeeId)
            AND r.dutyDate BETWEEN :from AND :to
            AND (:status IS NULL OR r.status = :status)
            """,
            countQuery = """
            SELECT COUNT(r) FROM RosterAssignmentEntity r
            WHERE (:employeeId IS NULL OR r.employeeId = :employeeId)
            AND r.dutyDate BETWEEN :from AND :to
            AND (:status IS NULL OR r.status = :status)
            """)
    Page<RosterAssignmentEntity> findRosterByFilters(
            @Param("employeeId") String employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") RosterAssignmentEntity.AssignmentStatus status,
            Pageable pageable);

    boolean existsByEmployeeIdAndFlightNumberAndDutyDate(
            String employeeId, String flightNumber, LocalDate dutyDate);
}
