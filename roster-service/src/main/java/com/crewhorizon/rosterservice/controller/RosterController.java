package com.crewhorizon.rosterservice.controller;

import com.crewhorizon.rosterservice.dto.request.CreateRosterAssignmentRequest;
import com.crewhorizon.rosterservice.dto.response.PagedResponse;
import com.crewhorizon.rosterservice.dto.response.RosterAssignmentResponse;
import com.crewhorizon.rosterservice.entity.RosterAssignmentEntity;
import com.crewhorizon.rosterservice.service.impl.RosterServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * ============================================================
 * Roster Assignment REST Controller
 * ============================================================
 * WHAT: HTTP entry point for all crew roster assignment operations.
 *
 * WHY HttpServletRequest for JWT token extraction:
 *       The roster-service needs to forward the JWT token to
 *       crew-service and flight-service during cross-service
 *       validation calls. Injecting HttpServletRequest gives
 *       direct access to the Authorization header without any
 *       additional SecurityContext manipulation.
 *
 * WHY @DateTimeFormat on LocalDate params:
 *       Spring cannot automatically parse ISO date strings from
 *       query parameters. @DateTimeFormat(iso = DATE) enables
 *       standard ISO-8601 format: ?from=2024-01-15
 * ============================================================
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rosters")
@RequiredArgsConstructor
@Tag(name = "Roster Management", description = "Crew Roster Assignment Management API")
public class RosterController {

    private final RosterServiceImpl rosterService;

    @PostMapping
    @Operation(summary = "Create a new crew roster assignment with FTL compliance validation")
    public ResponseEntity<RosterAssignmentResponse> createAssignment(
            @Valid @RequestBody CreateRosterAssignmentRequest request,
            @AuthenticationPrincipal UserDetails currentUser,
            HttpServletRequest httpRequest) {

        String jwtToken = extractToken(httpRequest);
        RosterAssignmentResponse response = rosterService.createRosterAssignment(
                request, currentUser.getUsername(), jwtToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/crew/{employeeId}")
    @Operation(summary = "Get crew schedule for a specific crew member and date range")
    public ResponseEntity<List<RosterAssignmentResponse>> getCrewSchedule(
            @PathVariable String employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(rosterService.getCrewSchedule(employeeId, from, to));
    }

    @GetMapping("/flight/{flightNumber}")
    @Operation(summary = "Get all crew assigned to a specific flight")
    public ResponseEntity<List<RosterAssignmentResponse>> getFlightCrew(
            @PathVariable String flightNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dutyDate) {

        return ResponseEntity.ok(rosterService.getFlightCrew(flightNumber, dutyDate));
    }

    @GetMapping
    @Operation(summary = "Get paginated roster assignments with optional filters")
    public ResponseEntity<PagedResponse<RosterAssignmentResponse>> getRosterByFilters(
            @RequestParam(required = false) String employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) RosterAssignmentEntity.AssignmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                rosterService.getRosterByFilters(employeeId, from, to, status, page, size));
    }

    @PatchMapping("/{assignmentId}/confirm")
    @Operation(summary = "Confirm a roster assignment (crew acknowledgment)")
    public ResponseEntity<RosterAssignmentResponse> confirmAssignment(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, String> body) {

        String employeeId = body.get("employeeId");
        return ResponseEntity.ok(rosterService.confirmAssignment(assignmentId, employeeId));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return "";
    }
}
