package com.crewhorizon.rosterservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rosters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Roster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String crewId;

    @Column(nullable = false)
    private String flightNumber;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    private String dutyRole;
    
    private String status;
}