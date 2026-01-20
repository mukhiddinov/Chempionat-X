package com.chempionat.bot.domain.repository;

import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByTournament(Tournament tournament);
    List<Team> findByUser(User user);
    Optional<Team> findByTournamentAndUser(Tournament tournament, User user);
}
