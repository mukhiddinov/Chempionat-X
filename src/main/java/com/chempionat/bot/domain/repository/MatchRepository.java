package com.chempionat.bot.domain.repository;

import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.enums.MatchStage;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByTournament(Tournament tournament);
    List<Match> findByTournamentAndState(Tournament tournament, MatchLifecycleState state);
    List<Match> findByTournamentAndHomeScoreIsNotNull(Tournament tournament);
    List<Match> findByTournamentAndRound(Tournament tournament, Integer round);
    List<Match> findByRound(Integer round);
    
    List<Match> findByHomeTeamOrAwayTeam(Team homeTeam, Team awayTeam);
    
    /**
     * Find match by ID with pessimistic write lock.
     * Use this when modifying match state to prevent concurrent modifications.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Match m WHERE m.id = :id")
    Optional<Match> findByIdWithLock(@Param("id") Long id);
    
    /**
     * Find matches by tournament and round with pessimistic write lock.
     * Use this when propagating winners to prevent race conditions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Match m WHERE m.tournament = :tournament AND m.round = :round")
    List<Match> findByTournamentAndRoundWithLock(@Param("tournament") Tournament tournament, @Param("round") Integer round);
    
    /**
     * Check if a next-round match already exists between two specific matches.
     * Used to prevent duplicate next-round match creation.
     */
    @Query("SELECT COUNT(m) > 0 FROM Match m WHERE m.tournament = :tournament " +
           "AND m.round = :round AND m.homeTeam = :homeTeam AND m.awayTeam = :awayTeam")
    boolean existsByTournamentAndRoundAndTeams(
            @Param("tournament") Tournament tournament,
            @Param("round") Integer round,
            @Param("homeTeam") Team homeTeam,
            @Param("awayTeam") Team awayTeam);
    
    @Query("SELECT m FROM Match m WHERE (m.homeTeam = :team OR m.awayTeam = :team) " +
           "AND DATE(m.scheduledTime) = :date")
    List<Match> findTodaysMatchesForTeam(@Param("team") Team team, @Param("date") LocalDate date);
    
    @Query("SELECT m FROM Match m WHERE (m.homeTeam = :team OR m.awayTeam = :team) " +
           "AND m.state = :state")
    List<Match> findByTeamAndState(@Param("team") Team team, @Param("state") MatchLifecycleState state);
    
    @Query("SELECT m FROM Match m WHERE m.tournament = :tournament " +
           "AND (m.homeTeam = :team OR m.awayTeam = :team)")
    List<Match> findByTournamentAndTeam(@Param("tournament") Tournament tournament, @Param("team") Team team);
    
    /**
     * Find matches by tournament and stage (for playoff brackets).
     */
    List<Match> findByTournamentAndStage(Tournament tournament, MatchStage stage);
    
    /**
     * Find matches ordered by bracket position (for rendering bracket tree).
     */
    @Query("SELECT m FROM Match m WHERE m.tournament = :tournament " +
           "AND m.bracketPosition IS NOT NULL " +
           "ORDER BY m.bracketPosition ASC")
    List<Match> findByTournamentOrderedByBracketPosition(@Param("tournament") Tournament tournament);
    
    /**
     * Find matches that feed into a specific match (to find previous round matches).
     */
    List<Match> findByNextMatch(Match nextMatch);
    
    /**
     * Find the final match of a tournament (highest bracket position or FINAL stage).
     */
    @Query("SELECT m FROM Match m WHERE m.tournament = :tournament AND m.stage = 'FINAL'")
    Optional<Match> findFinalMatch(@Param("tournament") Tournament tournament);
    
    /**
     * Count completed matches in a tournament (has scores).
     */
    @Query("SELECT COUNT(m) FROM Match m WHERE m.tournament = :tournament " +
           "AND m.homeScore IS NOT NULL AND m.awayScore IS NOT NULL " +
           "AND m.isBye = false")
    long countCompletedMatches(@Param("tournament") Tournament tournament);
    
    /**
     * Count total real matches in a tournament (excluding byes).
     */
    @Query("SELECT COUNT(m) FROM Match m WHERE m.tournament = :tournament AND m.isBye = false")
    long countRealMatches(@Param("tournament") Tournament tournament);
}
