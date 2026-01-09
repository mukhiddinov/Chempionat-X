package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.model.Team;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a team's standing in a league tournament.
 * Contains calculated statistics for league table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamStanding {
    private Team team;
    private int played;
    private int won;
    private int drawn;
    private int lost;
    private int goalsFor;
    private int goalsAgainst;
    private int goalDifference;
    private int points;
    
    /**
     * Calculate goal difference from goals for and against.
     */
    public void calculateGoalDifference() {
        this.goalDifference = this.goalsFor - this.goalsAgainst;
    }
    
    /**
     * Calculate points based on wins and draws (3 points per win, 1 per draw).
     */
    public void calculatePoints() {
        this.points = (this.won * 3) + this.drawn;
    }
}
