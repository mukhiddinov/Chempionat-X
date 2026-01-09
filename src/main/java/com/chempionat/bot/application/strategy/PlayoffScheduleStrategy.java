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
 * Playoff tournament strategy - generates knockout bracket matches.
 * Teams are paired in elimination rounds.
 */
@Slf4j
@Component
public class PlayoffScheduleStrategy implements TournamentScheduleStrategy {

    @Override
    public List<Match> generateMatches(Tournament tournament, List<Team> teams) {
        log.info("Generating playoff matches for tournament: {}", tournament.getName());
        
        List<Match> matches = new ArrayList<>();
        
        // Placeholder implementation: generate first round pairings
        // In a full implementation, this would create a proper bracket structure
        int teamCount = teams.size();
        int roundCount = (int) Math.ceil(Math.log(teamCount) / Math.log(2));
        
        // First round pairings
        for (int i = 0; i < teams.size(); i += 2) {
            if (i + 1 < teams.size()) {
                Match match = Match.builder()
                        .tournament(tournament)
                        .homeTeam(teams.get(i))
                        .awayTeam(teams.get(i + 1))
                        .state(MatchLifecycleState.CREATED)
                        .round(1)
                        .build();
                matches.add(match);
            }
        }
        
        log.info("Generated {} playoff matches for round 1", matches.size());
        // Future rounds would be generated dynamically based on results
        
        return matches;
    }

    @Override
    public boolean supports(Tournament tournament) {
        return tournament.getType() == TournamentType.PLAYOFF;
    }
}
