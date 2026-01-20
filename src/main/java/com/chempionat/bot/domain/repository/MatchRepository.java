package com.chempionat.bot.domain.repository;

import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByTournament(Tournament tournament);
    List<Match> findByTournamentAndState(Tournament tournament, MatchLifecycleState state);
    List<Match> findByTournamentAndHomeScoreIsNotNull(Tournament tournament);
    List<Match> findByTournamentAndRound(Tournament tournament, Integer round);
    List<Match> findByRound(Integer round);
    
    List<Match> findByHomeTeamOrAwayTeam(Team homeTeam, Team awayTeam);
    
    @Query("SELECT m FROM Match m WHERE (m.homeTeam = :team OR m.awayTeam = :team) " +
           "AND DATE(m.scheduledTime) = :date")
    List<Match> findTodaysMatchesForTeam(@Param("team") Team team, @Param("date") LocalDate date);
    
    @Query("SELECT m FROM Match m WHERE (m.homeTeam = :team OR m.awayTeam = :team) " +
           "AND m.state = :state")
    List<Match> findByTeamAndState(@Param("team") Team team, @Param("state") MatchLifecycleState state);
    
    @Query("SELECT m FROM Match m WHERE m.tournament = :tournament " +
           "AND (m.homeTeam = :team OR m.awayTeam = :team)")
    List<Match> findByTournamentAndTeam(@Param("tournament") Tournament tournament, @Param("team") Team team);
}
