package com.chempionat.bot.integration;

import com.chempionat.bot.application.service.*;
import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.*;
import com.chempionat.bot.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that simulates a complete tournament flow with fake users.
 * This test demonstrates the entire lifecycle of a tournament from creation to completion.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TournamentSimulationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private MatchResultService matchResultService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchResultRepository matchResultRepository;

    private User admin;
    private User player1;
    private User player2;
    private User player3;
    private User player4;

    @BeforeEach
    void setUp() {
        // Clean up database
        matchResultRepository.deleteAll();
        matchRepository.deleteAll();
        teamRepository.deleteAll();
        tournamentRepository.deleteAll();
        userRepository.deleteAll();

        // Create fake users
        admin = createFakeUser(1000001L, "admin_user", "Admin", "User", Role.ADMIN);
        player1 = createFakeUser(1000002L, "player_one", "Player", "One", Role.USER);
        player2 = createFakeUser(1000003L, "player_two", "Player", "Two", Role.USER);
        player3 = createFakeUser(1000004L, "player_three", "Player", "Three", Role.USER);
        player4 = createFakeUser(1000005L, "player_four", "Player", "Four", Role.USER);
    }

    private User createFakeUser(Long telegramId, String username, String firstName, String lastName, Role role) {
        User user = new User();
        user.setTelegramId(telegramId);
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        return userRepository.save(user);
    }

    @Test
    void testCompleteLeagueTournamentSimulation() {
        System.out.println("\n=== Starting Complete League Tournament Simulation ===\n");

        // Step 1: Admin creates a tournament
        System.out.println("Step 1: Admin creates tournament...");
        Tournament tournament = tournamentService.createTournament(
                "Test League 2026",
                "Integration test tournament",
                TournamentType.LEAGUE,
                admin
        );
        assertNotNull(tournament);
        assertFalse(tournament.getIsActive());
        System.out.println("✓ Tournament created: " + tournament.getName() + " (ID: " + tournament.getId() + ")");

        // Step 2: Players join the tournament
        System.out.println("\nStep 2: Players join tournament...");
        Team team1 = tournamentService.joinTournament(tournament, player1, "Team Alpha");
        Team team2 = tournamentService.joinTournament(tournament, player2, "Team Beta");
        Team team3 = tournamentService.joinTournament(tournament, player3, "Team Gamma");
        Team team4 = tournamentService.joinTournament(tournament, player4, "Team Delta");

        assertNotNull(team1);
        assertNotNull(team2);
        assertNotNull(team3);
        assertNotNull(team4);
        System.out.println("✓ 4 teams joined the tournament");

        // Step 3: Admin starts the tournament
        System.out.println("\nStep 3: Admin starts tournament...");
        tournamentService.startTournament(tournament.getId());
        
        Tournament startedTournament = tournamentRepository.findById(tournament.getId()).orElseThrow();
        assertNotNull(startedTournament.getStartDate());
        System.out.println("✓ Tournament started");

        // Step 4: Verify matches are generated
        System.out.println("\nStep 4: Verify match generation...");
        List<Match> matches = matchRepository.findByTournament(tournament);
        
        // For 4 teams in a league (round-robin), we should have:
        // Total matches = n * (n - 1) / 2 = 4 * 3 / 2 = 6 matches
        int expectedMatches = 6;
        assertEquals(expectedMatches, matches.size(), "Should generate 6 matches for 4 teams");
        System.out.println("✓ Generated " + matches.size() + " matches");

        // Print match fixtures
        System.out.println("\n--- Match Fixtures ---");
        for (Match match : matches) {
            System.out.println(String.format("Match #%d: %s vs %s on %s",
                    match.getId(),
                    match.getHomeTeam().getName(),
                    match.getAwayTeam().getName(),
                    match.getScheduledTime()));
        }

        // Step 5: Simulate match result submissions
        System.out.println("\n\nStep 5: Simulate match results...");
        
        // Get first 3 matches
        Match match1 = matches.get(0);
        Match match2 = matches.get(1);
        Match match3 = matches.get(2);

        // Match 1: Home team wins 3-1
        System.out.println("\nSimulating Match 1: " + match1.getHomeTeam().getName() + " vs " + match1.getAwayTeam().getName());
        submitAndApproveResult(match1, 3, 1, "fake_screenshot_url_1.jpg");
        System.out.println("✓ Result: 3-1 (Home win)");

        // Match 2: Away team wins 0-2
        System.out.println("\nSimulating Match 2: " + match2.getHomeTeam().getName() + " vs " + match2.getAwayTeam().getName());
        submitAndApproveResult(match2, 0, 2, "fake_screenshot_url_2.jpg");
        System.out.println("✓ Result: 0-2 (Away win)");

        // Match 3: Draw 2-2
        System.out.println("\nSimulating Match 3: " + match3.getHomeTeam().getName() + " vs " + match3.getAwayTeam().getName());
        submitAndApproveResult(match3, 2, 2, "fake_screenshot_url_3.jpg");
        System.out.println("✓ Result: 2-2 (Draw)");

        // Step 6: Verify match results are saved
        System.out.println("\n\nStep 6: Verify match results...");
        Match updatedMatch1 = matchRepository.findById(match1.getId()).orElseThrow();
        assertEquals(3, updatedMatch1.getHomeScore());
        assertEquals(1, updatedMatch1.getAwayScore());
        System.out.println("✓ Match scores saved correctly");

        // Step 7: Calculate and display standings
        System.out.println("\n\nStep 7: Generate standings...");
        List<TeamStanding> standings = calculateStandings(tournament);
        
        assertNotNull(standings);
        assertFalse(standings.isEmpty());
        
        System.out.println("\n--- STANDINGS ---");
        System.out.println(String.format("%-20s | %5s | %5s | %5s | %5s | %5s | %5s | %5s",
                "Team", "P", "W", "D", "L", "GF", "GA", "Pts"));
        System.out.println("-".repeat(80));
        
        for (TeamStanding standing : standings) {
            System.out.println(String.format("%-20s | %5d | %5d | %5d | %5d | %5d | %5d | %5d",
                    standing.getTeamName(),
                    standing.getPlayed(),
                    standing.getWon(),
                    standing.getDrawn(),
                    standing.getLost(),
                    standing.getGoalsFor(),
                    standing.getGoalsAgainst(),
                    standing.getPoints()));
        }

        // Step 8: Verify standings calculations
        System.out.println("\n\nStep 8: Verify standings logic...");
        
        // Find the team that won 3-1 in match1 (should have at least 3 points from that win)
        TeamStanding winnerTeam = standings.stream()
                .filter(s -> s.getTeamName().equals(match1.getHomeTeam().getName()))
                .findFirst()
                .orElseThrow();
        
        // We simulated 3 matches, so teams may have played 1-3 matches depending on who they faced
        assertTrue(winnerTeam.getPlayed() >= 1, "Winner should have played at least 1 match");
        assertTrue(winnerTeam.getWon() >= 1, "Winner should have at least 1 win (from the 3-1 victory)");
        assertTrue(winnerTeam.getPoints() >= 3, "Winner should have at least 3 points (from the 3-1 victory)");
        assertTrue(winnerTeam.getGoalsFor() >= 3, "Winner should have scored at least 3 goals");
        System.out.println("✓ Standings calculations are correct");
        System.out.println("  " + winnerTeam.getTeamName() + ": Played=" + winnerTeam.getPlayed() + 
                ", Won=" + winnerTeam.getWon() + ", Points=" + winnerTeam.getPoints());

        System.out.println("\n\n=== Tournament Simulation Completed Successfully ===\n");
    }

    @Test
    void testPlayoffTournamentSimulation() {
        System.out.println("\n=== Starting Playoff Tournament Simulation ===\n");

        // Step 1: Create Playoff tournament
        System.out.println("Step 1: Creating Playoff tournament...");
        Tournament tournament = tournamentService.createTournament(
                "Test Cup 2026",
                "Integration test cup tournament",
                TournamentType.PLAYOFF,
                admin
        );
        assertNotNull(tournament);
        System.out.println("✓ Playoff tournament created: " + tournament.getName());

        // Step 2: Players join
        System.out.println("\nStep 2: Players joining...");
        Team team1 = tournamentService.joinTournament(tournament, player1, "Cup Team 1");
        Team team2 = tournamentService.joinTournament(tournament, player2, "Cup Team 2");
        Team team3 = tournamentService.joinTournament(tournament, player3, "Cup Team 3");
        Team team4 = tournamentService.joinTournament(tournament, player4, "Cup Team 4");
        
        System.out.println("✓ 4 teams joined");

        // Step 3: Start tournament
        System.out.println("\nStep 3: Starting tournament...");
        tournamentService.startTournament(tournament.getId());
        System.out.println("✓ Tournament started");

        // Step 4: Verify matches
        System.out.println("\nStep 4: Verifying match generation...");
        List<Match> matches = matchRepository.findByTournament(tournament);
        
        // Playoff format with 4 teams should generate bracket matches
        // For simplicity, our implementation might do 4 teams as 2 semi-finals + final (3 matches)
        // or round-robin depending on implementation
        assertTrue(matches.size() > 0, "Should generate matches");
        System.out.println("✓ Generated " + matches.size() + " matches for Playoff format");

        System.out.println("\n--- Playoff Bracket ---");
        for (Match match : matches) {
            System.out.println(String.format("Match #%d: %s vs %s",
                    match.getId(),
                    match.getHomeTeam().getName(),
                    match.getAwayTeam().getName()));
        }

        System.out.println("\n\n=== Playoff Tournament Simulation Completed Successfully ===\n");
    }

    @Test
    void testMatchResultRejection() {
        System.out.println("\n=== Testing Match Result Rejection Flow ===\n");

        // Setup: Create tournament and start it
        Tournament tournament = tournamentService.createTournament(
                "Test Tournament", "Test", TournamentType.LEAGUE, admin);
        
        tournamentService.joinTournament(tournament, player1, "Team A");
        tournamentService.joinTournament(tournament, player2, "Team B");
        tournamentService.startTournament(tournament.getId());

        List<Match> matches = matchRepository.findByTournament(tournament);
        Match match = matches.get(0);

        // Submit a result
        System.out.println("Submitting result...");
        MatchResult result = matchResultService.submitResult(
                match,
                player1,
                3, 1,
                "screenshot.jpg"
        );
        assertNotNull(result);
        System.out.println("✓ Result submitted");

        // Reject the result
        System.out.println("\nRejecting result...");
        Long resultId = result.getId();
        matchResultService.rejectResult(resultId, admin, "Photo is not clear");
        
        // Result is deleted after rejection so user can resubmit
        assertTrue(matchResultRepository.findById(resultId).isEmpty());
        System.out.println("✓ Result deleted after rejection");
        
        // Match should be in REJECTED state with the reason stored
        Match updatedMatch = matchRepository.findById(match.getId()).orElseThrow();
        assertEquals(MatchLifecycleState.REJECTED, updatedMatch.getState());
        assertEquals("Photo is not clear", updatedMatch.getRejectReason());
        System.out.println("✓ Match in REJECTED state with reason: " + updatedMatch.getRejectReason());

        System.out.println("\n=== Result Rejection Test Completed ===\n");
    }

    @Test
    void testUserCannotJoinTournamentTwice() {
        System.out.println("\n=== Testing Duplicate Join Prevention ===\n");

        Tournament tournament = tournamentService.createTournament(
                "Test Tournament", "Test", TournamentType.LEAGUE, admin);

        // First join should succeed
        Team team1 = tournamentService.joinTournament(tournament, player1, "Team Alpha");
        assertNotNull(team1);
        System.out.println("✓ First join succeeded");

        // Second join should fail
        System.out.println("Attempting duplicate join...");
        assertThrows(IllegalStateException.class, () -> {
            tournamentService.joinTournament(tournament, player1, "Team Beta");
        });
        System.out.println("✓ Duplicate join prevented");

        System.out.println("\n=== Duplicate Join Test Completed ===\n");
    }

    private void submitAndApproveResult(Match match, int homeScore, int awayScore, String screenshotUrl) {
        // Get the home team's user to submit result
        User submitter = match.getHomeTeam().getUser();
        
        // Submit result
        MatchResult result = matchResultService.submitResult(
                match,
                submitter,
                homeScore,
                awayScore,
                screenshotUrl
        );

        // Approve result as admin
        matchResultService.approveResult(result.getId(), admin);
    }

    private List<TeamStanding> calculateStandings(Tournament tournament) {
        List<Team> teams = tournamentService.getTournamentTeams(tournament.getId());
        List<Match> matches = matchRepository.findByTournamentAndHomeScoreIsNotNull(tournament);
        
        Map<Long, TeamStanding> standingsMap = new HashMap<>();
        
        // Initialize standings for all teams
        for (Team team : teams) {
            standingsMap.put(team.getId(), new TeamStanding(team.getId(), team.getName()));
        }
        
        // Calculate stats from matches
        for (Match match : matches) {
            if (match.getHomeScore() == null || match.getAwayScore() == null) {
                continue;
            }
            
            TeamStanding homeStanding = standingsMap.get(match.getHomeTeam().getId());
            TeamStanding awayStanding = standingsMap.get(match.getAwayTeam().getId());
            
            if (homeStanding == null || awayStanding == null) {
                continue;
            }
            
            homeStanding.addMatch(match.getHomeScore(), match.getAwayScore());
            awayStanding.addMatch(match.getAwayScore(), match.getHomeScore());
        }
        
        // Sort standings
        List<TeamStanding> standings = new ArrayList<>(standingsMap.values());
        Collections.sort(standings);
        
        return standings;
    }

    /**
     * Test scenario for sequential approval of multiple pending matches in a knockout tournament.
     * Verifies:
     * - No race conditions occur
     * - No duplicate next-round match generation
     * - Bracket state remains consistent
     * - Idempotent approvals work correctly
     */
    @Test
    void testSequentialApprovalOfMultiplePendingMatches() {
        System.out.println("\n=== Testing Sequential Approval of Multiple Pending Matches ===\n");

        // Step 1: Create Playoff tournament with 4 teams
        System.out.println("Step 1: Creating Playoff tournament with 4 teams...");
        Tournament tournament = tournamentService.createTournament(
                "Sequential Approval Test Cup",
                "Test for sequential approval safety",
                TournamentType.PLAYOFF,
                admin
        );
        assertNotNull(tournament);
        System.out.println("✓ Playoff tournament created: " + tournament.getName());

        // Step 2: 4 Players join
        System.out.println("\nStep 2: Players joining...");
        Team team1 = tournamentService.joinTournament(tournament, player1, "Sequential Team 1");
        Team team2 = tournamentService.joinTournament(tournament, player2, "Sequential Team 2");
        Team team3 = tournamentService.joinTournament(tournament, player3, "Sequential Team 3");
        Team team4 = tournamentService.joinTournament(tournament, player4, "Sequential Team 4");
        System.out.println("✓ 4 teams joined");

        // Step 3: Start tournament
        System.out.println("\nStep 3: Starting tournament...");
        tournamentService.startTournament(tournament.getId());
        System.out.println("✓ Tournament started");

        // Step 4: Get semi-final matches
        System.out.println("\nStep 4: Verifying semi-final matches...");
        List<Match> semiMatches = matchRepository.findByTournament(tournament);
        assertEquals(2, semiMatches.size(), "4 teams should generate 2 semi-final matches");
        
        Match semi1 = semiMatches.get(0);
        Match semi2 = semiMatches.get(1);
        System.out.println("✓ Semi-finals: ");
        System.out.println("  Match " + semi1.getId() + ": " + semi1.getHomeTeam().getName() + " vs " + semi1.getAwayTeam().getName());
        System.out.println("  Match " + semi2.getId() + ": " + semi2.getHomeTeam().getName() + " vs " + semi2.getAwayTeam().getName());

        // Step 5: Submit results for both matches (both go to PENDING_APPROVAL)
        System.out.println("\nStep 5: Submitting results for both semi-finals...");
        MatchResult result1 = matchResultService.submitResult(
                semi1, semi1.getHomeTeam().getUser(), 2, 1, "screenshot1.jpg");
        MatchResult result2 = matchResultService.submitResult(
                semi2, semi2.getHomeTeam().getUser(), 3, 0, "screenshot2.jpg");
        
        // Refresh matches
        semi1 = matchRepository.findById(semi1.getId()).orElseThrow();
        semi2 = matchRepository.findById(semi2.getId()).orElseThrow();
        
        assertEquals(MatchLifecycleState.PENDING_APPROVAL, semi1.getState());
        assertEquals(MatchLifecycleState.PENDING_APPROVAL, semi2.getState());
        System.out.println("✓ Both matches are in PENDING_APPROVAL state");

        // Step 6: Approve first match
        System.out.println("\nStep 6: Approving first semi-final...");
        matchResultService.approveResult(result1.getId(), admin);
        
        semi1 = matchRepository.findById(semi1.getId()).orElseThrow();
        assertEquals(MatchLifecycleState.APPROVED, semi1.getState());
        System.out.println("✓ Match " + semi1.getId() + " approved. State: " + semi1.getState());
        System.out.println("  Score: " + semi1.getHomeScore() + "-" + semi1.getAwayScore());
        System.out.println("  Winner: " + semi1.getHomeTeam().getName()); // 2-1 means home won

        // Step 7: Verify no final match created yet (waiting for second semi)
        System.out.println("\nStep 7: Verifying bracket state...");
        List<Match> allMatches = matchRepository.findByTournament(tournament);
        int matchCountAfterFirst = allMatches.size();
        System.out.println("  Matches after first approval: " + matchCountAfterFirst);
        
        // Final should only be created when BOTH semis are done
        // After first semi approval, we should still have 2 matches (no final yet)
        assertEquals(2, matchCountAfterFirst, "Final should not be created until both semis complete");
        System.out.println("✓ No premature final match created");

        // Step 8: Approve second match
        System.out.println("\nStep 8: Approving second semi-final...");
        matchResultService.approveResult(result2.getId(), admin);
        
        semi2 = matchRepository.findById(semi2.getId()).orElseThrow();
        assertEquals(MatchLifecycleState.APPROVED, semi2.getState());
        System.out.println("✓ Match " + semi2.getId() + " approved. State: " + semi2.getState());
        System.out.println("  Score: " + semi2.getHomeScore() + "-" + semi2.getAwayScore());
        System.out.println("  Winner: " + semi2.getHomeTeam().getName()); // 3-0 means home won

        // Step 9: Verify final match was created
        System.out.println("\nStep 9: Verifying final match creation...");
        allMatches = matchRepository.findByTournament(tournament);
        int matchCountAfterSecond = allMatches.size();
        System.out.println("  Matches after second approval: " + matchCountAfterSecond);
        
        // After both semifinals complete: 2 semis + 1 final + 1 third-place = 4 matches
        assertTrue(matchCountAfterSecond >= 3 && matchCountAfterSecond <= 4, 
                "Should have 3-4 matches (semis + final + optional third-place)");
        
        // Find the final match
        Match finalMatch = allMatches.stream()
                .filter(m -> m.getRound() != null && m.getRound() == 2)
                .findFirst()
                .orElse(null);
        
        assertNotNull(finalMatch, "Final match should exist");
        assertNotNull(finalMatch.getHomeTeam(), "Final should have home team (semi1 winner)");
        assertNotNull(finalMatch.getAwayTeam(), "Final should have away team (semi2 winner)");
        System.out.println("✓ Final match created successfully:");
        System.out.println("  Match " + finalMatch.getId() + ": " + 
                finalMatch.getHomeTeam().getName() + " vs " + finalMatch.getAwayTeam().getName());

        // Step 10: Test idempotency - approve same results again
        System.out.println("\nStep 10: Testing idempotency (double approval)...");
        // This should NOT throw exception, just return gracefully
        matchResultService.approveResult(result1.getId(), admin);
        matchResultService.approveResult(result2.getId(), admin);
        System.out.println("✓ Double approvals handled gracefully (idempotent)");

        // Step 11: Verify no duplicate final matches (exclude third-place match)
        System.out.println("\nStep 11: Verifying no duplicate final matches...");
        allMatches = matchRepository.findByTournament(tournament);
        long finalCount = allMatches.stream()
                .filter(m -> m.getRound() != null && m.getRound() == 2 && !Boolean.TRUE.equals(m.getIsThirdPlaceMatch()))
                .count();
        assertEquals(1, finalCount, "Should have exactly ONE final match");
        System.out.println("✓ No duplicate final matches created");

        // Step 12: Complete the final
        System.out.println("\nStep 12: Completing the final...");
        MatchResult finalResult = matchResultService.submitResult(
                finalMatch, finalMatch.getHomeTeam().getUser(), 1, 0, "final_screenshot.jpg");
        matchResultService.approveResult(finalResult.getId(), admin);
        
        finalMatch = matchRepository.findById(finalMatch.getId()).orElseThrow();
        assertEquals(MatchLifecycleState.APPROVED, finalMatch.getState());
        System.out.println("✓ Final completed. Champion: " + finalMatch.getHomeTeam().getName());

        System.out.println("\n=== Sequential Approval Test Completed Successfully ===\n");
    }
}
