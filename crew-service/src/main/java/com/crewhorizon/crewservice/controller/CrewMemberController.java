package com.crewhorizon.crewservice.controller;

import com.crewhorizon.crewservice.dto.request.CreateCrewMemberRequest;
import com.crewhorizon.crewservice.dto.response.CrewMemberResponse;
import com.crewhorizon.crewservice.dto.response.PagedResponse;
import com.crewhorizon.crewservice.entity.CrewMemberEntity;
import com.crewhorizon.crewservice.service.impl.CrewServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ============================================================
 * Crew Member REST Controller
 * ============================================================
 * WHAT: HTTP API layer for crew member profile management.
 *       Exposes RESTful endpoints following REST conventions.
 *
 * WHY @AuthenticationPrincipal for audit:
 *       Every write operation (create/update/delete) captures
 *       WHO made the change for audit compliance.
 *       @AuthenticationPrincipal injects the current user's
 *       UserDetails directly from the SecurityContext —
 *       no need to parse the JWT again in the controller.
 *
 * WHY versioned API path (/api/v1/):
 *       Versioning enables the API to evolve without breaking
 *       existing consumers. When breaking changes are needed,
 *       a new /api/v2/ path can be introduced while /v1/ remains
 *       functional for backward compatibility.
 *
 * REST CONVENTIONS followed:
 *       GET /crew          - list (paginated)
 *       GET /crew/{id}     - get single
 *       POST /crew         - create
 *       PUT /crew/{id}     - full update
 *       PATCH /crew/{id}   - partial update
 *       DELETE /crew/{id}  - soft delete
 * ============================================================
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/crew")
@RequiredArgsConstructor
@Tag(name = "Crew Members", description = "Crew Member Profile Management API")
public class CrewMemberController {

    private final CrewServiceImpl crewService;

    /**
     * WHY @RequestParam with defaults (not @PathVariable):
     * Pagination, filtering, and sorting are query parameters —
     * they don't identify a resource, they QUALIFY the collection.
     * /crew?page=0&size=20&crewType=PILOT is RESTful.
     */
    @GetMapping
    @Operation(summary = "Get paginated list of crew members with optional filters")
    public ResponseEntity<PagedResponse<CrewMemberResponse>> getAllCrewMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection,
            @RequestParam(required = false) CrewMemberEntity.CrewType crewType,
            @RequestParam(required = false) String baseAirport,
            @RequestParam(required = false) CrewMemberEntity.DutyStatus dutyStatus) {

        PagedResponse<CrewMemberResponse> response = crewService.getAllCrewMembers(
                page, size, sortBy, sortDirection, crewType, baseAirport, dutyStatus);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{employeeId}")
    @Operation(summary = "Get crew member by employee ID")
    public ResponseEntity<CrewMemberResponse> getCrewMemberByEmployeeId(
            @PathVariable @Parameter(description = "Employee ID (e.g., EK123456)") String employeeId) {
        return ResponseEntity.ok(crewService.getCrewMemberByEmployeeId(employeeId));
    }

    @PostMapping
    @Operation(summary = "Create a new crew member profile")
    public ResponseEntity<CrewMemberResponse> createCrewMember(
            @Valid @RequestBody CreateCrewMemberRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        CrewMemberResponse response = crewService.createCrewMember(
                request, currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{employeeId}")
    @Operation(summary = "Update crew member profile")
    public ResponseEntity<CrewMemberResponse> updateCrewMember(
            @PathVariable String employeeId,
            @Valid @RequestBody CreateCrewMemberRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(
                crewService.updateCrewMember(employeeId, request, currentUser.getUsername()));
    }

    /**
     * WHY PATCH for status update (not PUT):
     * PATCH = partial update (only what's provided changes).
     * PUT = full replacement (all fields provided).
     * Updating only duty status is semantically a PATCH operation.
     */
    @PatchMapping("/{employeeId}/duty-status")
    @Operation(summary = "Update crew member duty status")
    public ResponseEntity<CrewMemberResponse> updateDutyStatus(
            @PathVariable String employeeId,
            @RequestBody Map<String, String> statusUpdate,
            @AuthenticationPrincipal UserDetails currentUser) {

        CrewMemberEntity.DutyStatus newStatus =
                CrewMemberEntity.DutyStatus.valueOf(statusUpdate.get("dutyStatus"));
        return ResponseEntity.ok(
                crewService.updateDutyStatus(employeeId, newStatus, currentUser.getUsername()));
    }

    /**
     * WHY 204 No Content (not 200 OK) for delete:
     * HTTP 204 signals the request was fulfilled but there is
     * no content to return. This is the semantic response for
     * DELETE operations that succeed.
     */
    @DeleteMapping("/{employeeId}")
    @Operation(summary = "Soft delete a crew member profile")
    public ResponseEntity<Void> deleteCrewMember(
            @PathVariable String employeeId,
            @AuthenticationPrincipal UserDetails currentUser) {
        crewService.deleteCrewMember(employeeId, currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * Scheduling endpoint: find available crew for an aircraft type.
     * WHY a dedicated endpoint (not filtered list):
     * This combines availability + qualification checking —
     * complex logic that benefits from a focused endpoint with
     * a meaningful semantic name.
     */
    @GetMapping("/available")
    @Operation(summary = "Find available crew qualified for specific aircraft type")
    public ResponseEntity<List<CrewMemberResponse>> getAvailableCrewByAircraft(
            @RequestParam String aircraftType,
            @RequestParam(required = false) String baseAirport) {
        return ResponseEntity.ok(
                crewService.getAvailableCrewByAircraftType(aircraftType, baseAirport));
    }
}
