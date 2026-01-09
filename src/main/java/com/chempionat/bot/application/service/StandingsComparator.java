package com.chempionat.bot.application.service;

import java.util.Comparator;

/**
 * Comparator for team standings implementing tiebreaker rules.
 * Sorting priority: Points → Goal Difference → Goals For.
 * Head-to-head is noted as optional/future enhancement.
 */
public class StandingsComparator implements Comparator<TeamStanding> {
    
    @Override
    public int compare(TeamStanding t1, TeamStanding t2) {
        // 1. Compare by points (higher is better)
        int pointsComparison = Integer.compare(t2.getPoints(), t1.getPoints());
        if (pointsComparison != 0) {
            return pointsComparison;
        }
        
        // 2. Compare by goal difference (higher is better)
        int goalDiffComparison = Integer.compare(t2.getGoalDifference(), t1.getGoalDifference());
        if (goalDiffComparison != 0) {
            return goalDiffComparison;
        }
        
        // 3. Compare by goals for (higher is better)
        int goalsForComparison = Integer.compare(t2.getGoalsFor(), t1.getGoalsFor());
        if (goalsForComparison != 0) {
            return goalsForComparison;
        }
        
        // 4. If still tied, maintain order (could add head-to-head here in the future)
        // Placeholder for head-to-head tiebreaker
        return 0;
    }
}
