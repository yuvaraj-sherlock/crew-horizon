package com.crewhorizon.crewservice.dto.response;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CrewQualificationResponse {
    private Long id;
    private String aircraftType;
    private QualificationTypeResponse qualificationType;
    private LocalDate issuedDate;
    private LocalDate expiryDate;
    private Boolean isCurrent;
    private Boolean isExpired;
    public enum QualificationTypeResponse {
        TYPE_RATING, ROUTE_CHECK, LINE_CHECK, INSTRUMENT_RATING,
        DANGEROUS_GOODS, SAFETY_TRAINING, FIRST_AID, RECURRENT_TRAINING
    }
}
