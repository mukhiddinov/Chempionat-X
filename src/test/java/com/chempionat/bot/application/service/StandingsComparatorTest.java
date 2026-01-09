package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.model.Team;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StandingsComparator.
 * Tests the tiebreaker rules: Points → Goal Difference → Goals For.
 */
class StandingsComparatorTest {

    private final StandingsComparator comparator = new StandingsComparator();

    @Test
    void testCompareByPoints() {
        // Team with more points should rank higher
        TeamStanding team1 = TeamStanding.builder()
                .points(10)
                .goalDifference(5)
                .goalsFor(15)
                .build();

        TeamStanding team2 = TeamStanding.builder()
                .points(7)
                .goalDifference(10)
                .goalsFor(20)
                .build();

        int result = comparator.compare(team1, team2);
        assertTrue(result < 0, "Team with more points should rank higher");
    }

    @Test
    void testCompareByGoalDifference_WhenPointsEqual() {
        // When points are equal, team with better goal difference should rank higher
        TeamStanding team1 = TeamStanding.builder()
                .points(10)
                .goalDifference(8)
                .goalsFor(15)
                .build();

        TeamStanding team2 = TeamStanding.builder()
                .points(10)
                .goalDifference(5)
                .goalsFor(20)
                .build();

        int result = comparator.compare(team1, team2);
        assertTrue(result < 0, "Team with better goal difference should rank higher when points are equal");
    }

    @Test
    void testCompareByGoalsFor_WhenPointsAndGoalDifferenceEqual() {
        // When points and goal difference are equal, team with more goals scored should rank higher
        TeamStanding team1 = TeamStanding.builder()
                .points(10)
                .goalDifference(5)
                .goalsFor(20)
                .build();

        TeamStanding team2 = TeamStanding.builder()
                .points(10)
                .goalDifference(5)
                .goalsFor(15)
                .build();

        int result = comparator.compare(team1, team2);
        assertTrue(result < 0, "Team with more goals should rank higher when points and GD are equal");
    }

    @Test
    void testCompletelyEqualTeams() {
        // When all criteria are equal, teams should be considered equal
        TeamStanding team1 = TeamStanding.builder()
                .points(10)
                .goalDifference(5)
                .goalsFor(15)
                .build();

        TeamStanding team2 = TeamStanding.builder()
                .points(10)
                .goalDifference(5)
                .goalsFor(15)
                .build();

        int result = comparator.compare(team1, team2);
        assertEquals(0, result, "Completely equal teams should have comparison result of 0");
    }

    @Test
    void testSortingMultipleTeams() {
        // Test sorting a list of teams
        List<TeamStanding> standings = new ArrayList<>();

        standings.add(TeamStanding.builder()
                .team(createMockTeam(1L, "Team A"))
                .points(10)
                .goalDifference(5)
                .goalsFor(15)
                .build());

        standings.add(TeamStanding.builder()
                .team(createMockTeam(2L, "Team B"))
                .points(15)
                .goalDifference(8)
                .goalsFor(20)
                .build());

        standings.add(TeamStanding.builder()
                .team(createMockTeam(3L, "Team C"))
                .points(10)
                .goalDifference(7)
                .goalsFor(18)
                .build());

        standings.sort(comparator);

        // Team B should be first (most points)
        assertEquals("Team B", standings.get(0).getTeam().getName());
        // Team C should be second (same points as A, but better GD)
        assertEquals("Team C", standings.get(1).getTeam().getName());
        // Team A should be third
        assertEquals("Team A", standings.get(2).getTeam().getName());
    }

    @Test
    void testNegativeGoalDifference() {
        // Test with negative goal differences
        TeamStanding team1 = TeamStanding.builder()
                .points(5)
                .goalDifference(-2)
                .goalsFor(10)
                .build();

        TeamStanding team2 = TeamStanding.builder()
                .points(5)
                .goalDifference(-5)
                .goalsFor(8)
                .build();

        int result = comparator.compare(team1, team2);
        assertTrue(result < 0, "Team with less negative GD should rank higher");
    }

    private Team createMockTeam(Long id, String name) {
        return Team.builder()
                .id(id)
                .name(name)
                .build();
    }
}
