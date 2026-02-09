package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing tournament rounds with bye logic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoundService {

    private final MatchRepository matchRepository;

    /**
     * Generate round-robin schedule with bye rounds for odd number of teams
     * Uses the circle method algorithm
     */
    @Transactional
    public List<Match> generateRoundRobinWithByes(Tournament tournament, List<Team> teams, int numberOfRounds) {
        List<Match> allMatches = new ArrayList<>();
        int teamCount = teams.size();
        
        if (teamCount < 2) {
            throw new IllegalArgumentException("At least 2 teams required");
        }

        // If odd number of teams, add null as bye placeholder
        List<Team> participants = new ArrayList<>(teams);
        if (teamCount % 2 != 0) {
            participants.add(null); // Bye placeholder
            teamCount = participants.size();
        }

        int totalRounds = teamCount - 1;
        
        // For each round requested (1 or 2)
        for (int cycle = 0; cycle < numberOfRounds; cycle++) {
            boolean swapHomeAway = numberOfRounds > 1 && (cycle % 2 == 1);
            // Generate all rounds for this cycle
            for (int round = 0; round < totalRounds; round++) {
                int actualRound = (cycle * totalRounds) + round + 1;
                List<Match> roundMatches = generateRoundMatches(tournament, participants, round, actualRound, swapHomeAway);
                allMatches.addAll(roundMatches);
            }
        }

        log.info("Generated {} matches for tournament {} with {} teams and {} rounds", 
                allMatches.size(), tournament.getId(), teams.size(), numberOfRounds);
        
        return allMatches;
    }

    /**
     * Generate matches for a single round using circle method
     */
    private List<Match> generateRoundMatches(Tournament tournament, List<Team> participants,
                                             int roundIndex, int actualRoundNumber, boolean swapHomeAway) {
        List<Match> roundMatches = new ArrayList<>();
        int n = participants.size();

        // Rotate teams (keep first team fixed, rotate others)
        List<Team> rotated = rotateTeams(participants, roundIndex);

        // Create pairs
        int halfSize = n / 2;
        for (int i = 0; i < halfSize; i++) {
            Team team1 = rotated.get(i);
            Team team2 = rotated.get(n - 1 - i);

            // CRITICAL: Prevent self-matches - a team cannot play against itself
            if (team1 != null && team2 != null && team1.getId().equals(team2.getId())) {
                log.error("Self-match detected! Team {} vs Team {} in round {}. Skipping.",
                        team1.getId(), team2.getId(), actualRoundNumber);
                continue; // Skip this match
            }

            // If either team is null (bye), create bye match
            if (team1 == null || team2 == null) {
                Team teamWithBye = (team1 == null) ? team2 : team1;
                Match byeMatch = createByeMatch(tournament, teamWithBye, actualRoundNumber);
                roundMatches.add(byeMatch);
            } else {
                Team home = swapHomeAway ? team2 : team1;
                Team away = swapHomeAway ? team1 : team2;
                Match match = createMatch(tournament, home, away, actualRoundNumber);
                roundMatches.add(match);
            }
        }

        return roundMatches;
    }

    /**
     * Rotate teams for round-robin (circle method)
     * First team stays fixed, others rotate clockwise
     */
    private List<Team> rotateTeams(List<Team> teams, int rotation) {
        if (rotation == 0) {
            return new ArrayList<>(teams);
        }
        
        List<Team> rotated = new ArrayList<>();
        rotated.add(teams.get(0)); // First team fixed
        
        int n = teams.size();
        for (int i = 1; i < n; i++) {
            int index = 1 + (i + rotation - 1) % (n - 1);
            rotated.add(teams.get(index));
        }
        
        return rotated;
    }

    /**
     * Create a regular match
     */
    private Match createMatch(Tournament tournament, Team home, Team away, int roundNumber) {
        return Match.builder()
                .tournament(tournament)
                .homeTeam(home)
                .awayTeam(away)
                .round(roundNumber)
                .state(MatchLifecycleState.CREATED)
                .scheduledTime(LocalDateTime.now().plusDays(roundNumber))
                .isBye(false)
                .build();
    }

    /**
     * Create a bye match (rest round)
     */
    private Match createByeMatch(Tournament tournament, Team team, int roundNumber) {
        return Match.builder()
                .tournament(tournament)
                .homeTeam(team)
                .awayTeam(team) // Same team for bye
                .round(roundNumber)
                .state(MatchLifecycleState.CREATED)
                .scheduledTime(LocalDateTime.now().plusDays(roundNumber))
                .isBye(true)
                .build();
    }

    /**
     * Get matches grouped by round
     */
    public Map<Integer, List<Match>> getMatchesByRound(Tournament tournament) {
        List<Match> matches = matchRepository.findByTournament(tournament);
        
        Map<Integer, List<Match>> matchesByRound = new HashMap<>();
        for (Match match : matches) {
            matchesByRound.computeIfAbsent(match.getRound(), k -> new ArrayList<>()).add(match);
        }
        
        return matchesByRound;
    }

    /**
     * Get all matches for a specific round
     */
    public List<Match> getMatchesForRound(Tournament tournament, int roundNumber) {
        List<Match> matches = matchRepository.findByTournament(tournament);
        return matches.stream()
                .filter(m -> m.getRound().equals(roundNumber))
                .toList();
    }

    /**
     * Calculate total number of rounds needed
     */
    public int calculateTotalRounds(int teamCount, int numberOfRounds) {
        if (teamCount < 2) return 0;
        
        // For round-robin, if odd teams, each team plays (n-1) matches
        // If even teams, each team plays (n-1) matches
        int roundsPerCycle = (teamCount % 2 == 0) ? teamCount - 1 : teamCount;
        
        return roundsPerCycle * numberOfRounds;
    }

    /**
     * Check if a team has a bye in a specific round
     */
    public boolean hasByeInRound(Team team, int roundNumber) {
        return matchRepository.findByHomeTeamOrAwayTeam(team, team)
                .stream()
                .anyMatch(m -> m.getRound().equals(roundNumber) && m.getIsBye());
    }

    /**
     * Get the maximum round number for a tournament
     */
    public int getMaxRoundNumber(Tournament tournament) {
        return matchRepository.findByTournament(tournament)
                .stream()
                .mapToInt(Match::getRound)
                .max()
                .orElse(0);
    }
}
