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
public class TeamStanding implements Comparable<TeamStanding> {
    private Long teamId;
    private String teamName;
    private Team team;
    private int played;
    private int won;
    private int drawn;
    private int lost;
    private int goalsFor;
    private int goalsAgainst;
    private int goalDifference;
    private int points;
    
    public TeamStanding(Long teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.played = 0;
        this.won = 0;
        this.drawn = 0;
        this.lost = 0;
        this.goalsFor = 0;
        this.goalsAgainst = 0;
        this.goalDifference = 0;
        this.points = 0;
    }
    
    /**
     * Add match result to this team's statistics
     */
    public void addMatch(int goalsFor, int goalsAgainst) {
        this.played++;
        this.goalsFor += goalsFor;
        this.goalsAgainst += goalsAgainst;
        
        if (goalsFor > goalsAgainst) {
            this.won++;
            this.points += 3;
        } else if (goalsFor == goalsAgainst) {
            this.drawn++;
            this.points += 1;
        } else {
            this.lost++;
        }
        
        this.goalDifference = this.goalsFor - this.goalsAgainst;
    }
    
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
    
    @Override
    public int compareTo(TeamStanding other) {
        // Sort by points (descending)
        int pointsComparison = Integer.compare(other.points, this.points);
        if (pointsComparison != 0) {
            return pointsComparison;
        }
        
        // Then by goal difference (descending)
        int gdComparison = Integer.compare(other.goalDifference, this.goalDifference);
        if (gdComparison != 0) {
            return gdComparison;
        }
        
        // Then by goals for (descending)
        return Integer.compare(other.goalsFor, this.goalsFor);
    }
}
