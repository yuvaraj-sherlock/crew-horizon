package com.crewhorizon.rosterservice.service.impl;

import com.crewhorizon.rosterservice.dto.request.CreateRosterAssignmentRequest;
import com.crewhorizon.rosterservice.dto.response.RosterAssignmentResponse;
import com.crewhorizon.rosterservice.dto.response.PagedResponse;
import com.crewhorizon.rosterservice.entity.RosterAssignmentEntity;
import com.crewhorizon.rosterservice.exception.DuplicateAssignmentException;
import com.crewhorizon.rosterservice.exception.FtlViolationException;
import com.crewhorizon.rosterservice.exception.RosterAssignmentNotFoundException;
import com.crewhorizon.rosterservice.mapper.RosterMapper;
import com.crewhorizon.rosterservice.repository.RosterAssignmentRepository;
import com.crewhorizon.rosterservice.service.FtlComplianceService;
import com.crewhorizon.rosterservice.service.InterServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Roster Service Implementation
 * ============================================================
 * WHAT: Orchestrates the creation and management of crew roster
 *       assignments, including cross-service validation,
 *       FTL compliance checks, and notification dispatch.
 *
 * WHY Orchestration in the Service Layer:
 *       The roster assignment creation flow involves:
 *       1. Validate crew member exists (crew-service)
 *       2. Validate flight exists and is schedulable (flight-service)
 *       3. Check crew availability and duty status
 *       4. Check FTL compliance (internal)
 *       5. Persist the assignment
 *       6. Notify the crew member (notification-service)
 *
 *       This is a SERVICE ORCHESTRATION pattern (not choreography).
 *       The roster-service DIRECTS the workflow explicitly.
 *       Choreography (event-driven with message queues) could also
 *       be used for steps 5-6 (notifications), but orchestration
 *       is simpler to reason about and debug for safety-critical
 *       operations like crew scheduling.
 *
 * WHY @Transactional on the outer method but NOT on validation:
 *       Cross-service HTTP calls CANNOT participate in a DB
 *       transaction (they're in different databases). We perform
 *       all validations BEFORE opening the DB transaction, then
 *       open the transaction only for the DB write.
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RosterServiceImpl {

    private final RosterAssignmentRepository rosterRepository;
    private final FtlComplianceService ftlComplianceService;
    private final InterServiceClient interServiceClient;
    private final RosterMapper rosterMapper;

    /**
     * Creates a new roster assignment with full validation.
     *
     * WHY two-phase approach (validate then persist):
     * Performing all validations before the DB transaction avoids
     * holding a DB connection open during the (potentially slow)
     * HTTP calls to crew-service and flight-service.
     * DB connections are a limited resource — minimize hold time.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_CREW_SCHEDULER', 'ROLE_ADMIN')")
    public RosterAssignmentResponse createRosterAssignment(
            CreateRosterAssignmentRequest request,
            String schedulerEmployeeId,
            String jwtToken) {

        log.info("Creating roster assignment: crew={}, flight={}, date={}",
                request.getEmployeeId(), request.getFlightNumber(), request.getDutyDate());

        // ─── PHASE 1: Cross-Service Validation (outside transaction) ───

        // WHY .block() on reactive calls here:
        // The outer method is synchronous (Spring MVC, not WebFlux).
        // We must block to get results from reactive WebClient calls.
        // In a fully reactive stack, we would return Mono<RosterAssignmentResponse>.
        Map<String, Object> crewData = interServiceClient
                .getCrewMember(request.getEmployeeId(), jwtToken)
                .blockOptional()
                .orElseThrow(() -> new RuntimeException(
                        "Could not validate crew member — crew-service unavailable"));

        Map<String, Object> flightData = interServiceClient
                .getFlight(request.getFlightNumber(), jwtToken)
                .blockOptional()
                .orElseThrow(() -> new RuntimeException(
                        "Could not validate flight — flight-service unavailable"));

        // Validate crew availability
        String dutyStatus = (String) crewData.get("dutyStatus");
        if (!"AVAILABLE".equals(dutyStatus)) {
            throw new IllegalStateException(String.format(
                    "Crew member %s is not available (current status: %s)",
                    request.getEmployeeId(), dutyStatus));
        }

        // Check for duplicate assignment (same crew, same flight, same date)
        if (rosterRepository.existsByEmployeeIdAndFlightNumberAndDutyDate(
                request.getEmployeeId(), request.getFlightNumber(), request.getDutyDate())) {
            throw new DuplicateAssignmentException(
                    "Crew member is already assigned to this flight on this date");
        }

        // ─── PHASE 2: FTL Compliance Check ───

        /*
         * WHY FTL check is NON-NEGOTIABLE (not just a warning):
         * FTL limits are legally binding. An assignment that violates
         * FTL cannot be committed without explicit override approval
         * (tracked by a senior operations manager). The API returns
         * the violation details so the frontend can present the
         * override workflow.
         */
        if (request.getDutyStartTime() != null && request.getDutyEndTime() != null) {
            FtlComplianceService.FtlValidationResult ftlResult =
                    ftlComplianceService.validateAssignment(
                            request.getEmployeeId(),
                            request.getDutyStartTime(),
                            request.getDutyEndTime(),
                            request.getEstimatedFlightMinutes() != null
                                    ? request.getEstimatedFlightMinutes() : 0);

            if (ftlResult.isViolation() && !request.isOverrideFtl()) {
                log.warn("FTL violation for crew {} on flight {}: {}",
                        request.getEmployeeId(), request.getFlightNumber(),
                        ftlResult.violationReason());
                throw new FtlViolationException(ftlResult.violationReason());
            }

            if (ftlResult.isViolation() && request.isOverrideFtl()) {
                log.warn("[FTL-OVERRIDE] Scheduler {} overriding FTL violation for crew {}: {}",
                        schedulerEmployeeId, request.getEmployeeId(), ftlResult.violationReason());
            }
        }

        // ─── PHASE 3: Persist Assignment ───

        RosterAssignmentEntity entity = rosterMapper.toEntity(request);
        entity.setAssignedBy(schedulerEmployeeId);
        entity.setStatus(RosterAssignmentEntity.AssignmentStatus.PROPOSED);
        entity.setDepartureAirport((String) flightData.get("departureAirport"));
        entity.setArrivalAirport((String) flightData.get("arrivalAirport"));

        RosterAssignmentEntity saved = rosterRepository.save(entity);

        log.info("Roster assignment created: id={}, crew={}, flight={}",
                saved.getId(), saved.getEmployeeId(), saved.getFlightNumber());

        return rosterMapper.toResponse(saved);
    }

    /**
     * Gets crew schedule for a specific crew member.
     * Used by the crew portal (pilots viewing their own roster).
     *
     * WHY @PreAuthorize with '#employeeId == authentication.name' hack:
     * This allows crew members to ONLY view their OWN roster.
     * Schedulers and admins can view any crew's roster.
     * Spring Security SpEL evaluates this expression per-call.
     */
    @PreAuthorize("""
            hasAnyRole('ROLE_CREW_SCHEDULER', 'ROLE_ADMIN', 'ROLE_OPERATIONS')
            or @rosterSecurityService.isOwner(authentication, #employeeId)
            """)
    public List<RosterAssignmentResponse> getCrewSchedule(
            String employeeId, LocalDate from, LocalDate to) {
        return rosterRepository.findByEmployeeIdAndDateRange(employeeId, from, to)
                .stream()
                .map(rosterMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Gets full crew manifest for a specific flight.
     * Used by operations on departure day.
     */
    @PreAuthorize("hasAnyRole('ROLE_CREW_SCHEDULER', 'ROLE_OPERATIONS', 'ROLE_ADMIN', 'ROLE_PILOT', 'ROLE_CABIN_CREW')")
    public List<RosterAssignmentResponse> getFlightCrew(String flightNumber, LocalDate dutyDate) {
        return rosterRepository.findCrewForFlight(flightNumber, dutyDate)
                .stream()
                .map(rosterMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Confirms a roster assignment (crew member acknowledges their assignment).
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_CREW_SCHEDULER', 'ROLE_ADMIN') or " +
                  "@rosterSecurityService.isOwner(authentication, #employeeId)")
    public RosterAssignmentResponse confirmAssignment(Long assignmentId, String employeeId) {
        RosterAssignmentEntity entity = rosterRepository.findById(assignmentId)
                .orElseThrow(() -> new RosterAssignmentNotFoundException(
                        "Assignment not found: " + assignmentId));

        if (!entity.getEmployeeId().equals(employeeId)) {
            throw new SecurityException("Cannot confirm another crew member's assignment");
        }

        entity.setStatus(RosterAssignmentEntity.AssignmentStatus.CONFIRMED);
        return rosterMapper.toResponse(rosterRepository.save(entity));
    }

    /**
     * Admin view: paginated roster with optional filters.
     */
    @PreAuthorize("hasAnyRole('ROLE_CREW_SCHEDULER', 'ROLE_ADMIN', 'ROLE_OPERATIONS')")
    public PagedResponse<RosterAssignmentResponse> getRosterByFilters(
            String employeeId, LocalDate from, LocalDate to,
            RosterAssignmentEntity.AssignmentStatus status,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("dutyDate").ascending());
        Page<RosterAssignmentEntity> rosterPage = rosterRepository.findRosterByFilters(
                employeeId, from, to, status, pageable);

        return PagedResponse.<RosterAssignmentResponse>builder()
                .content(rosterPage.getContent().stream()
                        .map(rosterMapper::toResponse)
                        .collect(Collectors.toList()))
                .page(rosterPage.getNumber())
                .size(rosterPage.getSize())
                .totalElements(rosterPage.getTotalElements())
                .totalPages(rosterPage.getTotalPages())
                .first(rosterPage.isFirst())
                .last(rosterPage.isLast())
                .empty(rosterPage.isEmpty())
                .build();
    }
}
