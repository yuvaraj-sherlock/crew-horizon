package com.crewhorizon.rosterservice.mapper;
import com.crewhorizon.rosterservice.dto.request.CreateRosterAssignmentRequest;
import com.crewhorizon.rosterservice.dto.response.RosterAssignmentResponse;
import com.crewhorizon.rosterservice.entity.RosterAssignmentEntity;
import org.mapstruct.*;
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RosterMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "isFtlCompliant", constant = "true")
    @Mapping(target = "ftlViolationReason", ignore = true)
    @Mapping(target = "departureAirport", ignore = true)
    @Mapping(target = "arrivalAirport", ignore = true)
    @Mapping(target = "assignedBy", ignore = true)
    @Mapping(target = "flightTimeMinutes", source = "estimatedFlightMinutes")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RosterAssignmentEntity toEntity(CreateRosterAssignmentRequest request);
    RosterAssignmentResponse toResponse(RosterAssignmentEntity entity);
}
