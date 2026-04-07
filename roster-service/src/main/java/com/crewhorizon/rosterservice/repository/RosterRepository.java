package com.crewhorizon.rosterservice.repository;

import com.crewhorizon.rosterservice.entity.Roster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RosterRepository extends JpaRepository<Roster, Long> {
    List<Roster> findByCrewId(String crewId);
}