package com.crewhorizon.rosterservice.service;

import com.crewhorizon.rosterservice.entity.Roster;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RosterService {
    Flux<Roster> getRostersByCrewId(String crewId);
    Mono<Roster> createRoster(Roster roster);
    Mono<Roster> getRosterById(Long id);
}