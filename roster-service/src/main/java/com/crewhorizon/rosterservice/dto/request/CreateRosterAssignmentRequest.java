package com.crewhorizon.rosterservice.dto.request;
import com.crewhorizon.rosterservice.entity.RosterAssignmentEntity;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.ZonedDateTime;
@Data
public class CreateRosterAssignmentRequest {
    @NotBlank @Pattern(regexp = "^[A-Z]{2}[0-9]{6}$")
    private String employeeId;
    @NotBlank @Size(max = 10)
    private String flightNumber;
    @NotNull
    private RosterAssignmentEntity.AssignedRole assignedRole;
    @NotNull
    private LocalDate dutyDate;
    private ZonedDateTime reportTime;
    private ZonedDateTime dutyStartTime;
    private ZonedDateTime dutyEndTime;
    private Integer estimatedFlightMinutes;
    private String notes;
    private boolean overrideFtl = false;
}
