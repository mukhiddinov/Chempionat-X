package com.chempionat.bot.domain.repository;

import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByTournament(Tournament tournament);
    List<Team> findByUser(User user);
    Optional<Team> findByTournamentAndUser(Tournament tournament, User user);
    long countByTournament(Tournament tournament);
    
    /**
     * Check if a team with the given name already exists in the tournament (case-insensitive).
     */
    @Query("SELECT COUNT(t) > 0 FROM Team t WHERE t.tournament = :tournament AND LOWER(t.name) = LOWER(:name)")
    boolean existsByTournamentAndNameIgnoreCase(@Param("tournament") Tournament tournament, @Param("name") String name);
}
