package com.crewhorizon.rosterservice.service;

import com.crewhorizon.rosterservice.entity.RosterAssignmentEntity;
import com.crewhorizon.rosterservice.repository.RosterAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * ============================================================
 * Flight Time Limitations (FTL) Compliance Service
 * ============================================================
 * WHAT: Enforces ICAO Annex 6 and EU-OPS Flight and Duty Time
 *       Limitation rules before any crew assignment is committed.
 *
 * WHY a dedicated FTL service class:
 *       FTL rules are legally mandated, frequently updated
 *       (regulatory changes), and highly complex. Isolating
 *       them in a dedicated class means:
 *       1. Rules are testable in isolation (unit tests without
 *          DB or HTTP dependencies)
 *       2. Regulatory updates require changes in ONE place
 *       3. Compliance audit has a single class to review
 *       4. Future enhancement: plug in different ruleset for
 *          different regulatory zones (EU OPS vs FAR 117)
 *
 * KEY FTL LIMITS IMPLEMENTED (simplified for demonstration):
 *       - Max flight duty period (FDP): 13 hours
 *       - Max flight time in 28 days: 100 hours
 *       - Minimum rest period between duties: 10 hours
 *       - Max cumulative duty in 7 consecutive days: 60 hours
 *
 * WHY these rules are safety-critical:
 *       Crew fatigue is a major contributing factor in aviation
 *       accidents. FTL rules exist to prevent fatigue-related
 *       incidents. Violating them is not just illegal —
 *       it is potentially fatal.
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FtlComplianceService {

    private final RosterAssignmentRepository rosterRepository;

    // ICAO/EU-OPS FTL Constants (hours converted to minutes)
    private static final int MAX_FDP_MINUTES = 13 * 60;              // 13 hours max duty period
    private static final int MIN_REST_MINUTES = 10 * 60;             // 10 hours minimum rest
    private static final int MAX_FLIGHT_TIME_28_DAYS_MINUTES = 100 * 60; // 100 hours in 28 days
    private static final int MAX_DUTY_7_DAYS_MINUTES = 60 * 60;     // 60 hours in 7 days

    /**
     * Performs complete FTL validation for a proposed assignment.
     *
     * WHY validate BEFORE persisting (not after):
     * Pre-validation follows the "fail fast" principle — detect
     * violations immediately and give the scheduler actionable
     * feedback BEFORE the DB transaction commits. Post-validation
     * would require rollback and leave the system in a partial state.
     *
     * @return FtlValidationResult — encapsulates compliance status and reason
     */
    public FtlValidationResult validateAssignment(
            String employeeId,
            ZonedDateTime proposedDutyStart,
            ZonedDateTime proposedDutyEnd,
            int proposedFlightMinutes) {

        log.debug("FTL validation for crew {} | dutyStart={} | dutyEnd={}",
                employeeId, proposedDutyStart, proposedDutyEnd);

        // 1. CHECK: Proposed duty period itself doesn't exceed FDP limit
        int proposedDutyMinutes = (int) Duration.between(
                proposedDutyStart, proposedDutyEnd).toMinutes();

        if (proposedDutyMinutes > MAX_FDP_MINUTES) {
            return FtlValidationResult.violation(String.format(
                    "Proposed duty period of %d minutes exceeds FDP limit of %d minutes",
                    proposedDutyMinutes, MAX_FDP_MINUTES));
        }

        // 2. CHECK: Minimum rest since last duty
        ZonedDateTime lastDutyEnd = getLastDutyEndTime(employeeId, proposedDutyStart);
        if (lastDutyEnd != null) {
            long minutesSinceLastDuty = Duration.between(lastDutyEnd, proposedDutyStart).toMinutes();
            if (minutesSinceLastDuty < MIN_REST_MINUTES) {
                return FtlValidationResult.violation(String.format(
                        "Insufficient rest period: %d minutes since last duty (minimum: %d minutes)",
                        minutesSinceLastDuty, MIN_REST_MINUTES));
            }
        }

        // 3. CHECK: Cumulative flight time in last 28 days
        LocalDate windowStart28 = proposedDutyStart.toLocalDate().minusDays(28);
        int flightTime28Days = getTotalFlightMinutes(employeeId, windowStart28,
                proposedDutyStart.toLocalDate());

        if (flightTime28Days + proposedFlightMinutes > MAX_FLIGHT_TIME_28_DAYS_MINUTES) {
            return FtlValidationResult.violation(String.format(
                    "28-day flight time limit exceeded: %d + %d = %d minutes (limit: %d minutes)",
                    flightTime28Days, proposedFlightMinutes,
                    flightTime28Days + proposedFlightMinutes,
                    MAX_FLIGHT_TIME_28_DAYS_MINUTES));
        }

        // 4. CHECK: Cumulative duty time in last 7 days
        LocalDate windowStart7 = proposedDutyStart.toLocalDate().minusDays(7);
        int dutyTime7Days = getTotalDutyMinutes(employeeId, windowStart7,
                proposedDutyStart.toLocalDate());

        if (dutyTime7Days + proposedDutyMinutes > MAX_DUTY_7_DAYS_MINUTES) {
            return FtlValidationResult.violation(String.format(
                    "7-day duty time limit exceeded: %d + %d = %d minutes (limit: %d minutes)",
                    dutyTime7Days, proposedDutyMinutes,
                    dutyTime7Days + proposedDutyMinutes,
                    MAX_DUTY_7_DAYS_MINUTES));
        }

        log.debug("FTL validation PASSED for crew {}", employeeId);
        return FtlValidationResult.compliant();
    }

    private ZonedDateTime getLastDutyEndTime(String employeeId, ZonedDateTime before) {
        List<RosterAssignmentEntity> recentAssignments = rosterRepository
                .findLastCompletedAssignment(employeeId, before.toLocalDate());

        if (recentAssignments.isEmpty()) return null;

        return recentAssignments.stream()
                .filter(a -> a.getDutyEndTime() != null)
                .max(java.util.Comparator.comparing(RosterAssignmentEntity::getDutyEndTime))
                .map(RosterAssignmentEntity::getDutyEndTime)
                .orElse(null);
    }

    private int getTotalFlightMinutes(String employeeId,
                                       LocalDate from, LocalDate to) {
        return rosterRepository.sumFlightMinutesBetween(employeeId, from, to)
                .orElse(0);
    }

    private int getTotalDutyMinutes(String employeeId,
                                     LocalDate from, LocalDate to) {
        return rosterRepository.sumDutyMinutesBetween(employeeId, from, to)
                .orElse(0);
    }

    /**
     * FTL Validation Result value object.
     *
     * WHY a value object instead of throwing an exception:
     * FTL violations are BUSINESS RULE outcomes, not exceptional
     * conditions. Using a result object allows the caller to:
     * - Display the specific violation reason to the scheduler
     * - Decide whether to override (with approval workflow)
     * - Log violations for compliance reporting
     * Exceptions should be reserved for unexpected system errors.
     */
    public record FtlValidationResult(boolean compliant, String violationReason) {

        public static FtlValidationResult compliant() {
            return new FtlValidationResult(true, null);
        }

        public static FtlValidationResult violation(String reason) {
            return new FtlValidationResult(false, reason);
        }

        public boolean isViolation() {
            return !compliant;
        }
    }
}
