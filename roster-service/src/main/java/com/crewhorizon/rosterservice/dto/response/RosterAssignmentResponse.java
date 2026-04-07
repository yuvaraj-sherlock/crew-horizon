package com.crewhorizon.rosterservice.dto.response;
import com.crewhorizon.rosterservice.entity.RosterAssignmentEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RosterAssignmentResponse {
    private Long id;
    private String employeeId;
    private String flightNumber;
    private RosterAssignmentEntity.AssignedRole assignedRole;
    private LocalDate dutyDate;
    private ZonedDateTime reportTime;
    private ZonedDateTime dutyStartTime;
    private ZonedDateTime dutyEndTime;
    private Integer flightTimeMinutes;
    private Integer totalDutyMinutes;
    private RosterAssignmentEntity.AssignmentStatus status;
    private String departureAirport;
    private String arrivalAirport;
    private Boolean isFtlCompliant;
    private String ftlViolationReason;
    private String assignedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
