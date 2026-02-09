package com.chempionat.bot.domain.enums;

/**
 * Represents the stage of a match in a tournament.
 * Used for both League and Playoff formats.
 */
public enum MatchStage {
    // League stages
    GROUP_STAGE,
    LEAGUE_ROUND,
    
    // Playoff stages
    ROUND_OF_64,
    ROUND_OF_32,
    ROUND_OF_16,
    QUARTER_FINAL,
    SEMI_FINAL,
    THIRD_PLACE,
    FINAL
}
