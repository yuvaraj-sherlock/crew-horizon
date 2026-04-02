package com.crewhorizon.crewservice.mapper;

import com.crewhorizon.crewservice.dto.request.CreateCrewMemberRequest;
import com.crewhorizon.crewservice.dto.response.CrewMemberResponse;
import com.crewhorizon.crewservice.entity.CrewMemberEntity;
import com.crewhorizon.crewservice.entity.CrewQualificationEntity;
import org.mapstruct.*;

import java.util.List;

/**
 * ============================================================
 * Crew Member Mapper (MapStruct)
 * ============================================================
 * WHAT: Compile-time generated mapper for converting between
 *       CrewMemberEntity and DTOs.
 *
 * WHY MapStruct over manual mapping or ModelMapper:
 *       1. COMPILE-TIME: Errors caught at build time, not runtime.
 *          ModelMapper uses reflection — mapping errors only
 *          surface in runtime tests.
 *       2. ZERO RUNTIME OVERHEAD: MapStruct generates plain Java
 *          methods — no reflection, no performance cost.
 *       3. TYPE SAFETY: IDE autocompletion works on generated code.
 *       4. EXPLICIT MAPPING: @Mapping annotations make the
 *          transformation fully visible and auditable.
 *          No "magic" field name matching that breaks silently.
 *
 * WHY componentModel = "spring":
 *       Generates @Component-annotated mapper implementations,
 *       making them injectable as Spring beans.
 *       Without this, mappers would need new instantiation.
 *
 * WHY @BeanMapping(nullValuePropertyMappingStrategy = IGNORE):
 *       During partial updates (PATCH), null fields in the
 *       update request should NOT overwrite existing values.
 *       IGNORE strategy skips null source values.
 * ============================================================
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface CrewMemberMapper {

    /**
     * Maps CreateCrewMemberRequest DTO to CrewMemberEntity.
     *
     * WHY @Mapping(target = "id", ignore = true):
     * The DB auto-generates the ID. Allowing the client to set it
     * would be a security vulnerability (ID injection attacks).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dutyStatus", constant = "AVAILABLE")
    @Mapping(target = "qualifications", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "seniorityNumber", ignore = true)
    CrewMemberEntity toEntity(CreateCrewMemberRequest request);

    /**
     * Maps entity to full response DTO.
     *
     * WHY @Mapping for fullName:
     * fullName is a derived field (firstName + " " + lastName).
     * MapStruct can't derive this automatically — we use an
     * @AfterMapping hook to compute it.
     */
    @Mapping(target = "fullName", ignore = true)
    @Mapping(target = "qualifications", ignore = true)
    CrewMemberResponse toResponse(CrewMemberEntity entity);

    /**
     * WHY @AfterMapping:
     * For fields that require logic beyond simple property copying
     * (like concatenating names), @AfterMapping runs after
     * MapStruct's generated mapping to apply the custom logic.
     */
    @AfterMapping
    default void setDerivedFields(@MappingTarget CrewMemberResponse response,
                                   CrewMemberEntity entity) {
        response.setFullName(entity.getFirstName() + " " + entity.getLastName());
    }

    List<CrewMemberResponse> toResponseList(List<CrewMemberEntity> entities);

    /**
     * Partial update: only update non-null fields from source.
     * WHY @MappingTarget: Tells MapStruct to update an EXISTING
     * object rather than creating a new one.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "employeeId", ignore = true)  // employeeId is immutable
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntityFromRequest(CreateCrewMemberRequest request,
                                  @MappingTarget CrewMemberEntity entity);

    // Qualification mapping
    @Mapping(target = "isExpired", expression = "java(qualification.isExpired())")
    @Mapping(target = "isExpiringSoon", expression = "java(qualification.isExpiringSoon(30))")
    @Mapping(target = "qualificationType",
             expression = "java(mapQualificationType(qualification.getQualificationType()))")
    CrewMemberResponse.QualificationResponse toQualificationResponse(
            CrewQualificationEntity qualification);

    default CrewMemberResponse.QualificationResponse.QualificationTypeResponse mapQualificationType(
            CrewQualificationEntity.QualificationType type) {
        return CrewMemberResponse.QualificationResponse.QualificationTypeResponse.valueOf(type.name());
    }
}
