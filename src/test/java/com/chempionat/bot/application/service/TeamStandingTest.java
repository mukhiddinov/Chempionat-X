package com.chempionat.bot.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TeamStanding calculations.
 */
class TeamStandingTest {

    @Test
    void testCalculateGoalDifference() {
        TeamStanding standing = TeamStanding.builder()
                .goalsFor(20)
                .goalsAgainst(15)
                .build();

        standing.calculateGoalDifference();

        assertEquals(5, standing.getGoalDifference(), "Goal difference should be goalsFor - goalsAgainst");
    }

    @Test
    void testCalculateGoalDifference_Negative() {
        TeamStanding standing = TeamStanding.builder()
                .goalsFor(10)
                .goalsAgainst(18)
                .build();

        standing.calculateGoalDifference();

        assertEquals(-8, standing.getGoalDifference(), "Goal difference can be negative");
    }

    @Test
    void testCalculatePoints() {
        TeamStanding standing = TeamStanding.builder()
                .won(5)
                .drawn(3)
                .lost(2)
                .build();

        standing.calculatePoints();

        // 5 wins * 3 points + 3 draws * 1 point = 18 points
        assertEquals(18, standing.getPoints(), "Points should be calculated as (wins * 3) + draws");
    }

    @Test
    void testCalculatePoints_NoWins() {
        TeamStanding standing = TeamStanding.builder()
                .won(0)
                .drawn(5)
                .lost(5)
                .build();

        standing.calculatePoints();

        assertEquals(5, standing.getPoints(), "Points should be only from draws when no wins");
    }

    @Test
    void testCalculatePoints_NoDraws() {
        TeamStanding standing = TeamStanding.builder()
                .won(8)
                .drawn(0)
                .lost(2)
                .build();

        standing.calculatePoints();

        assertEquals(24, standing.getPoints(), "Points should be only from wins when no draws");
    }

    @Test
    void testCalculateBothMetrics() {
        TeamStanding standing = TeamStanding.builder()
                .won(6)
                .drawn(2)
                .lost(2)
                .goalsFor(25)
                .goalsAgainst(18)
                .build();

        standing.calculatePoints();
        standing.calculateGoalDifference();

        assertEquals(20, standing.getPoints());
        assertEquals(7, standing.getGoalDifference());
    }
}
