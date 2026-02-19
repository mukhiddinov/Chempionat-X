package com.chempionat.bot.application.strategy;

import com.chempionat.bot.application.service.SingleEliminationService;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Playoff tournament strategy - generates knockout bracket matches.
 * Uses SingleEliminationService for proper bracket generation with
 * stage detection, BYE handling, and winner propagation support.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayoffScheduleStrategy implements TournamentScheduleStrategy {

    private final SingleEliminationService singleEliminationService;

    @Override
    public List<Match> generateMatches(Tournament tournament, List<Team> teams) {
        log.info("Generating playoff bracket for tournament: {} with {} teams", 
                tournament.getName(), teams.size());
        
        if (teams.size() < 2) {
            throw new IllegalStateException("At least 2 teams required for playoff tournament");
        }
        
        // Delegate to SingleEliminationService for full bracket generation
        List<Match> matches = singleEliminationService.generateBracket(tournament, teams);
        
        log.info("Generated {} playoff bracket matches for tournament {}", 
                matches.size(), tournament.getId());
        
        return matches;
    }

    @Override
    public boolean supports(Tournament tournament) {
        return tournament.getType() == TournamentType.PLAYOFF;
    }
}
