package com.chempionat.bot.application.strategy;

import com.chempionat.bot.application.service.RoundService;
import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * League tournament strategy - generates round-robin matches with bye round support.
 * Uses RoundService to handle proper round-robin scheduling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeagueScheduleStrategy implements TournamentScheduleStrategy {

    private final RoundService roundService;

    @Override
    public List<Match> generateMatches(Tournament tournament, List<Team> teams) {
        log.info("Generating league matches for tournament: {} with {} teams", 
                 tournament.getName(), teams.size());
        
        // Get numberOfRounds from tournament, default to 1 if not set
        int numberOfRounds = tournament.getNumberOfRounds() != null ? tournament.getNumberOfRounds() : 1;
        
        // Use RoundService to generate round-robin with bye rounds
        List<Match> matches = roundService.generateRoundRobinWithByes(tournament, teams, numberOfRounds);
        
        log.info("Generated {} league matches", matches.size());
        return matches;
    }

    @Override
    public boolean supports(Tournament tournament) {
        return tournament.getType() == TournamentType.LEAGUE;
    }
}
