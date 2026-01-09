package com.chempionat.bot.application.strategy;

import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;

import java.util.List;

/**
 * Strategy interface for tournament scheduling formats.
 * Different tournament types (League, Playoff) implement different scheduling logic.
 */
public interface TournamentScheduleStrategy {
    
    /**
     * Generate matches for a tournament based on the strategy.
     * 
     * @param tournament The tournament for which to generate matches
     * @param teams List of teams participating in the tournament
     * @return List of generated matches
     */
    List<Match> generateMatches(Tournament tournament, List<Team> teams);
    
    /**
     * Check if this strategy supports the given tournament type.
     * 
     * @param tournament The tournament to check
     * @return true if this strategy can handle the tournament type
     */
    boolean supports(Tournament tournament);
}
