package com.crewhorizon.crewservice.service.impl;

import com.crewhorizon.crewservice.dto.request.CreateCrewMemberRequest;
import com.crewhorizon.crewservice.dto.response.CrewMemberResponse;
import com.crewhorizon.crewservice.dto.response.PagedResponse;
import com.crewhorizon.crewservice.entity.CrewMemberEntity;
import com.crewhorizon.crewservice.exception.CrewMemberNotFoundException;
import com.crewhorizon.crewservice.exception.DuplicateCrewMemberException;
import com.crewhorizon.crewservice.mapper.CrewMemberMapper;
import com.crewhorizon.crewservice.repository.CrewMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Crew Service Implementation
 * ============================================================
 * WHAT: Business logic for crew member lifecycle management.
 *       Handles CRUD operations with caching, security, and
 *       pagination.
 *
 * WHY @Cacheable on frequently-read crew profiles:
 *       Scheduling systems query individual crew profiles
 *       repeatedly during roster building. Redis caching
 *       eliminates redundant DB round-trips:
 *       - First call: DB query (5-20ms) + cache population
 *       - Subsequent calls: Redis (< 1ms)
 *       For a rostering engine processing 200 crew members,
 *       this saves 200 * 20ms = 4 seconds per roster build.
 *
 * WHY @PreAuthorize at service layer (not just controller):
 *       Defense in depth. Service methods can be called
 *       programmatically (not just via HTTP), so security
 *       must be enforced at the service level too.
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrewServiceImpl {

    private final CrewMemberRepository crewMemberRepository;
    private final CrewMemberMapper crewMemberMapper;

    /**
     * Gets a paginated list of crew members.
     *
     * WHY @Transactional(readOnly = true) at class level:
     * Default for all methods is readOnly = true (reads).
     * This tells Hibernate to skip dirty-checking on entities
     * (since we're not modifying them), reducing CPU overhead
     * significantly for read-heavy operations.
     * Write methods override with @Transactional (readOnly=false).
     *
     * WHY sortBy and sortDirection parameters:
     * Different consumers need different orderings:
     * - Scheduling view: sort by seniority
     * - HR view: sort by last name alphabetically
     * - Compliance view: sort by qualification expiry
     */
    public PagedResponse<CrewMemberResponse> getAllCrewMembers(
            int page, int size, String sortBy, String sortDirection,
            CrewMemberEntity.CrewType crewType,
            String baseAirport,
            CrewMemberEntity.DutyStatus dutyStatus) {

        Sort sort = Sort.by(
                Sort.Direction.fromString(sortDirection),
                sortBy
        );
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<CrewMemberEntity> crewPage;
        if (crewType != null || baseAirport != null || dutyStatus != null) {
            crewPage = crewMemberRepository.findByFilters(crewType, baseAirport, dutyStatus, pageable);
        } else {
            crewPage = crewMemberRepository.findAllByIsDeletedFalse(pageable);
        }

        List<CrewMemberResponse> responses = crewPage.getContent()
                .stream()
                .map(crewMemberMapper::toResponse)
                .collect(Collectors.toList());

        return PagedResponse.<CrewMemberResponse>builder()
                .content(responses)
                .page(crewPage.getNumber())
                .size(crewPage.getSize())
                .totalElements(crewPage.getTotalElements())
                .totalPages(crewPage.getTotalPages())
                .first(crewPage.isFirst())
                .last(crewPage.isLast())
                .empty(crewPage.isEmpty())
                .build();
    }

    /**
     * Gets a single crew member by employee ID.
     *
     * WHY @Cacheable with cache name "crew-members":
     * Individual crew profiles are frequently needed by:
     * - Roster building (same crew checked multiple times)
     * - Flight assignment validation
     * - Scheduling dashboard rendering
     * Caching by employeeId (the natural business key) is
     * more semantic than caching by DB surrogate key (id).
     *
     * WHY "unless = '#result == null'":
     * Never cache null results — a null cache entry would
     * mask future inserts of the same employeeId.
     */
    @Cacheable(value = "crew-members", key = "#employeeId",
               unless = "#result == null")
    public CrewMemberResponse getCrewMemberByEmployeeId(String employeeId) {
        log.debug("Fetching crew member: {}", employeeId);
        CrewMemberEntity entity = crewMemberRepository
                .findByEmployeeIdAndIsDeletedFalse(employeeId)
                .orElseThrow(() -> new CrewMemberNotFoundException(
                        "Crew member not found: " + employeeId));

        CrewMemberResponse response = crewMemberMapper.toResponse(entity);

        // Load qualifications for detail view
        response.setQualifications(
                entity.getQualifications().stream()
                        .map(crewMemberMapper::toQualificationResponse)
                        .collect(Collectors.toList())
        );

        return response;
    }

    /**
     * Creates a new crew member profile.
     *
     * WHY @Transactional (overrides class-level readOnly=true):
     * Write operations need full transaction support with
     * dirty checking and flush on commit.
     *
     * WHY @PreAuthorize("hasAnyRole(...)"):
     * Only schedulers and HR managers can create crew profiles.
     * Pilots and cabin crew should NOT be able to create profiles.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_CREW_SCHEDULER', 'ROLE_HR_MANAGER', 'ROLE_ADMIN')")
    @CacheEvict(value = "crew-members-list", allEntries = true)
    public CrewMemberResponse createCrewMember(CreateCrewMemberRequest request,
                                                String createdBy) {
        log.info("Creating crew member: employeeId={}", request.getEmployeeId());

        if (crewMemberRepository.existsByEmployeeId(request.getEmployeeId())) {
            throw new DuplicateCrewMemberException(
                    "Employee ID already exists: " + request.getEmployeeId());
        }
        if (crewMemberRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateCrewMemberException(
                    "Email already registered: " + request.getEmail());
        }

        CrewMemberEntity entity = crewMemberMapper.toEntity(request);
        entity.setCreatedBy(createdBy);

        // Auto-assign seniority based on hire date relative to existing crew
        assignSeniorityNumber(entity);

        CrewMemberEntity saved = crewMemberRepository.save(entity);
        log.info("Crew member created: id={}, employeeId={}", saved.getId(), saved.getEmployeeId());

        return crewMemberMapper.toResponse(saved);
    }

    /**
     * Updates a crew member profile.
     *
     * WHY @CachePut (not @CacheEvict + re-fetch):
     * @CachePut updates the cache with the new value immediately
     * after the method executes — no cache miss on next read.
     * @CacheEvict would remove the entry, causing a DB hit on
     * the next read. @CachePut is more efficient for updates.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_CREW_SCHEDULER', 'ROLE_HR_MANAGER', 'ROLE_ADMIN')")
    @CachePut(value = "crew-members", key = "#employeeId")
    @CacheEvict(value = "crew-members-list", allEntries = true)
    public CrewMemberResponse updateCrewMember(String employeeId,
                                                CreateCrewMemberRequest request,
                                                String updatedBy) {
        CrewMemberEntity entity = crewMemberRepository
                .findByEmployeeIdAndIsDeletedFalse(employeeId)
                .orElseThrow(() -> new CrewMemberNotFoundException(
                        "Crew member not found: " + employeeId));

        crewMemberMapper.updateEntityFromRequest(request, entity);
        entity.setUpdatedBy(updatedBy);

        CrewMemberEntity saved = crewMemberRepository.save(entity);
        return crewMemberMapper.toResponse(saved);
    }

    /**
     * Updates crew duty status.
     *
     * WHY separate endpoint for duty status:
     * Status changes are high-frequency, time-sensitive operations
     * (crew checks in/out, gets grounded, etc.). A dedicated
     * method is more explicit and avoids loading/sending the full
     * profile just to change one field.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_CREW_SCHEDULER', 'ROLE_OPERATIONS', 'ROLE_ADMIN')")
    @CacheEvict(value = "crew-members", key = "#employeeId")
    public CrewMemberResponse updateDutyStatus(String employeeId,
                                                CrewMemberEntity.DutyStatus newStatus,
                                                String updatedBy) {
        CrewMemberEntity entity = crewMemberRepository
                .findByEmployeeIdAndIsDeletedFalse(employeeId)
                .orElseThrow(() -> new CrewMemberNotFoundException(employeeId));

        CrewMemberEntity.DutyStatus oldStatus = entity.getDutyStatus();
        entity.setDutyStatus(newStatus);
        entity.setUpdatedBy(updatedBy);

        CrewMemberEntity saved = crewMemberRepository.save(entity);
        log.info("Duty status updated: employeeId={}, {} -> {}",
                employeeId, oldStatus, newStatus);

        return crewMemberMapper.toResponse(saved);
    }

    /**
     * Soft deletes a crew member.
     *
     * WHY @CacheEvict(allEntries=true) on delete:
     * Deleting one crew member might affect list/filter caches
     * that included them. Evicting all list caches ensures
     * consistency. Individual profile cache is also evicted.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_HR_MANAGER', 'ROLE_ADMIN')")
    @CacheEvict(value = {"crew-members", "crew-members-list"}, allEntries = true)
    public void deleteCrewMember(String employeeId, String deletedBy) {
        CrewMemberEntity entity = crewMemberRepository
                .findByEmployeeIdAndIsDeletedFalse(employeeId)
                .orElseThrow(() -> new CrewMemberNotFoundException(employeeId));

        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setUpdatedBy(deletedBy);
        crewMemberRepository.save(entity);

        log.info("Crew member soft-deleted: employeeId={}, by={}", employeeId, deletedBy);
    }

    public List<CrewMemberResponse> getAvailableCrewByAircraftType(String aircraftType,
                                                                     String baseAirport) {
        return crewMemberRepository
                .findAvailableCrewByAircraftType(aircraftType, baseAirport)
                .stream()
                .map(crewMemberMapper::toResponse)
                .collect(Collectors.toList());
    }

    private void assignSeniorityNumber(CrewMemberEntity entity) {
        // Seniority = count of existing crew with earlier hire date + 1
        // Simplified implementation — production would use a stored procedure
        long seniorCount = crewMemberRepository.findAllByIsDeletedFalse(
                PageRequest.of(0, Integer.MAX_VALUE)).getTotalElements();
        entity.setSeniorityNumber((int) seniorCount + 1);
    }
}
