package com.chempionat.bot.application.strategy;

import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * League tournament strategy - generates round-robin matches.
 * Each team plays against every other team.
 */
@Slf4j
@Component
public class LeagueScheduleStrategy implements TournamentScheduleStrategy {

    @Override
    public List<Match> generateMatches(Tournament tournament, List<Team> teams) {
        log.info("Generating league matches for tournament: {}", tournament.getName());
        
        List<Match> matches = new ArrayList<>();
        int round = 1;
        
        // Round-robin: each team plays every other team
        for (int i = 0; i < teams.size(); i++) {
            for (int j = i + 1; j < teams.size(); j++) {
                Match match = Match.builder()
                        .tournament(tournament)
                        .homeTeam(teams.get(i))
                        .awayTeam(teams.get(j))
                        .state(MatchLifecycleState.CREATED)
                        .round(round)
                        .build();
                matches.add(match);
                
                // Optionally add return match (home/away swap)
                // This is common in league formats
            }
            round++;
        }
        
        log.info("Generated {} league matches", matches.size());
        return matches;
    }

    @Override
    public boolean supports(Tournament tournament) {
        return tournament.getType() == TournamentType.LEAGUE;
    }
}
