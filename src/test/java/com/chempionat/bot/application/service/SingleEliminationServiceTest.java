package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.enums.MatchStage;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.TeamRepository;
import com.chempionat.bot.domain.repository.TournamentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SingleEliminationService.
 * Tests bracket generation, stage detection, and winner propagation logic.
 */
@ExtendWith(MockitoExtension.class)
class SingleEliminationServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private TournamentRepository tournamentRepository;
    
    @Mock
    private TeamRepository teamRepository;

    @Mock
    private NotificationService notificationService;

    @Captor
    private ArgumentCaptor<List<Match>> matchListCaptor;

    private SingleEliminationService service;

    @BeforeEach
    void setUp() {
        service = new SingleEliminationService(matchRepository, tournamentRepository, teamRepository, notificationService);
    }

    @Test
    void testDetectStartingStage_2Teams() {
        MatchStage stage = service.detectStartingStage(2);
        assertEquals(MatchStage.FINAL, stage);
    }

    @Test
    void testDetectStartingStage_4Teams() {
        MatchStage stage = service.detectStartingStage(4);
        assertEquals(MatchStage.SEMI_FINAL, stage);
    }

    @Test
    void testDetectStartingStage_8Teams() {
        MatchStage stage = service.detectStartingStage(8);
        assertEquals(MatchStage.QUARTER_FINAL, stage);
    }

    @Test
    void testDetectStartingStage_16Teams() {
        MatchStage stage = service.detectStartingStage(16);
        assertEquals(MatchStage.ROUND_OF_16, stage);
    }

    @Test
    void testDetectStartingStage_32Teams() {
        MatchStage stage = service.detectStartingStage(32);
        assertEquals(MatchStage.ROUND_OF_32, stage);
    }

    @Test
    void testDetectStartingStage_64Teams() {
        MatchStage stage = service.detectStartingStage(64);
        assertEquals(MatchStage.ROUND_OF_64, stage);
    }

    @Test
    void testDetectStartingStage_OddTeams_RoundsUp() {
        // 5 teams should use 8-team bracket (next power of 2)
        MatchStage stage = service.detectStartingStage(5);
        assertEquals(MatchStage.QUARTER_FINAL, stage);
    }

    @Test
    void testCalculateBracketSize_PowerOf2() {
        assertEquals(2, service.calculateBracketSize(2));
        assertEquals(4, service.calculateBracketSize(4));
        assertEquals(8, service.calculateBracketSize(8));
        assertEquals(16, service.calculateBracketSize(16));
    }

    @Test
    void testCalculateBracketSize_NotPowerOf2() {
        assertEquals(4, service.calculateBracketSize(3));
        assertEquals(8, service.calculateBracketSize(5));
        assertEquals(8, service.calculateBracketSize(6));
        assertEquals(8, service.calculateBracketSize(7));
        assertEquals(16, service.calculateBracketSize(9));
    }

    @Test
    void testGenerateBracket_4Teams() {
        // Arrange
        Tournament tournament = createTournament(1L, "Test Tournament");
        List<Team> teams = createTeams(4);

        when(matchRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Match> matches = invocation.getArgument(0);
            // Assign IDs to matches
            long id = 1L;
            for (Match m : matches) {
                m.setId(id++);
            }
            return matches;
        });

        // Act
        List<Match> matches = service.generateBracket(tournament, teams);

        // Assert
        verify(matchRepository).saveAll(matchListCaptor.capture());
        List<Match> savedMatches = matchListCaptor.getValue();

        // 4 teams = 2 first-round matches only (semifinals)
        // Next rounds are created dynamically as winners advance
        assertEquals(2, savedMatches.size());

        // Verify stages - all matches should be SEMI_FINAL (first round for 4 teams)
        long semiFinalCount = savedMatches.stream()
                .filter(m -> m.getStage() == MatchStage.SEMI_FINAL)
                .count();

        assertEquals(2, semiFinalCount);

        // Verify first round matches have teams assigned
        for (Match match : savedMatches) {
            assertNotNull(match.getHomeTeam());
            assertNotNull(match.getAwayTeam());
            assertFalse(match.getIsBye());
        }
    }

    @Test
    void testGenerateBracket_8Teams() {
        // Arrange
        Tournament tournament = createTournament(1L, "Test Tournament");
        List<Team> teams = createTeams(8);

        when(matchRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Match> matches = invocation.getArgument(0);
            long id = 1L;
            for (Match m : matches) {
                m.setId(id++);
            }
            return matches;
        });

        // Act
        List<Match> matches = service.generateBracket(tournament, teams);

        // Assert
        verify(matchRepository).saveAll(matchListCaptor.capture());
        List<Match> savedMatches = matchListCaptor.getValue();

        // 8 teams = 4 first-round matches (QF) only
        // Next rounds are created dynamically as winners advance
        assertEquals(4, savedMatches.size());

        // Verify stages - all should be QUARTER_FINAL (first round for 8 teams)
        long qfCount = savedMatches.stream()
                .filter(m -> m.getStage() == MatchStage.QUARTER_FINAL)
                .count();
        assertEquals(4, qfCount);
    }

    @Test
    void testGenerateBracket_OddTeams_WithByes() {
        // Arrange
        Tournament tournament = createTournament(1L, "Test Tournament");
        List<Team> teams = createTeams(5); // 5 teams need 8-team bracket with 3 byes

        when(matchRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Match> matches = invocation.getArgument(0);
            long id = 1L;
            for (Match m : matches) {
                m.setId(id++);
            }
            return matches;
        });

        // Act
        List<Match> matches = service.generateBracket(tournament, teams);

        // Assert
        verify(matchRepository, atLeastOnce()).saveAll(matchListCaptor.capture());
        
        // Get the first saveAll call (initial first-round matches)
        List<Match> savedMatches = matchListCaptor.getAllValues().get(0);

        // 8-team bracket, but only first round = 4 matches
        assertEquals(4, savedMatches.size());

        // Count BYE matches
        long byeCount = savedMatches.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsBye()))
                .count();

        assertEquals(3, byeCount, "Should have 3 BYE matches for 5 teams in 8-team bracket");

        // BYE matches should be auto-approved
        for (Match match : savedMatches) {
            if (Boolean.TRUE.equals(match.getIsBye())) {
                assertEquals(MatchLifecycleState.APPROVED, match.getState());
            }
        }
    }

    @Test
    void testGenerateBracket_FirstRoundOnly() {
        // Test that only first-round matches are created, next rounds are created dynamically
        Tournament tournament = createTournament(1L, "Test Tournament");
        List<Team> teams = createTeams(4);

        when(matchRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Match> matches = invocation.getArgument(0);
            long id = 1L;
            for (Match m : matches) {
                m.setId(id++);
            }
            return matches;
        });

        // Act
        List<Match> matches = service.generateBracket(tournament, teams);

        // Assert
        verify(matchRepository).saveAll(matchListCaptor.capture());
        List<Match> savedMatches = matchListCaptor.getValue();

        // All matches should be round 1
        assertTrue(savedMatches.stream().allMatch(m -> m.getRound() == 1),
                "Only first-round matches should be created initially");
        
        // No match should have nextMatch set initially (created dynamically)
        assertTrue(savedMatches.stream().allMatch(m -> m.getNextMatch() == null),
                "Next matches are created dynamically when winners advance");
    }

    @Test
    void testDetermineWinner_HomeWins() {
        Match match = createMatch(1L);
        match.setHomeScore(3);
        match.setAwayScore(1);

        Team winner = service.determineWinner(match);

        assertEquals(match.getHomeTeam(), winner);
    }

    @Test
    void testDetermineWinner_AwayWins() {
        Match match = createMatch(1L);
        match.setHomeScore(1);
        match.setAwayScore(3);

        Team winner = service.determineWinner(match);

        assertEquals(match.getAwayTeam(), winner);
    }

    @Test
    void testDetermineWinner_Draw() {
        Match match = createMatch(1L);
        match.setHomeScore(2);
        match.setAwayScore(2);

        Team winner = service.determineWinner(match);

        assertNull(winner, "Draw should return null winner");
    }

    @Test
    void testDetermineLoser() {
        Match match = createMatch(1L);
        match.setHomeScore(3);
        match.setAwayScore(1);

        Team loser = service.determineLoser(match);

        assertEquals(match.getAwayTeam(), loser);
    }

    @Test
    void testIsDraw() {
        assertTrue(service.isDraw(2, 2));
        assertTrue(service.isDraw(0, 0));
        assertFalse(service.isDraw(3, 1));
        assertFalse(service.isDraw(1, 3));
        assertFalse(service.isDraw(null, 1));
        assertFalse(service.isDraw(1, null));
    }

    @Test
    void testGetStageDisplayName() {
        assertEquals("Final", service.getStageDisplayName(MatchStage.FINAL));
        assertEquals("Yarim final", service.getStageDisplayName(MatchStage.SEMI_FINAL));
        assertEquals("Chorak final", service.getStageDisplayName(MatchStage.QUARTER_FINAL));
        assertEquals("1/8", service.getStageDisplayName(MatchStage.ROUND_OF_16));
        assertEquals("1/16", service.getStageDisplayName(MatchStage.ROUND_OF_32));
        assertEquals("1/32", service.getStageDisplayName(MatchStage.ROUND_OF_64));
    }

    @Test
    void testGenerateBracket_ThrowsForLessThan2Teams() {
        Tournament tournament = createTournament(1L, "Test");
        List<Team> teams = createTeams(1);

        assertThrows(IllegalArgumentException.class, 
                () -> service.generateBracket(tournament, teams));
    }

    // Helper methods

    private Tournament createTournament(Long id, String name) {
        return Tournament.builder()
                .id(id)
                .name(name)
                .type(TournamentType.PLAYOFF)
                .isActive(true)
                .build();
    }

    private List<Team> createTeams(int count) {
        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            User user = User.builder()
                    .id((long) i)
                    .telegramId((long) (1000 + i))
                    .username("player" + i)
                    .firstName("Player " + i)
                    .build();
            
            Team team = Team.builder()
                    .id((long) i)
                    .name("Team " + i)
                    .user(user)
                    .build();
            teams.add(team);
        }
        return teams;
    }

    private Match createMatch(Long id) {
        Tournament tournament = createTournament(1L, "Test");
        List<Team> teams = createTeams(2);
        
        return Match.builder()
                .id(id)
                .tournament(tournament)
                .homeTeam(teams.get(0))
                .awayTeam(teams.get(1))
                .state(MatchLifecycleState.CREATED)
                .round(1)
                .build();
    }
    
    @Test
    void testDetermineWinner_ByeMatch() {
        // BYE matches have homeScore=0, awayScore=0 but winner should be homeTeam
        Match match = createMatch(1L);
        match.setIsBye(true);
        match.setHomeScore(0);
        match.setAwayScore(0);

        Team winner = service.determineWinner(match);

        assertEquals(match.getHomeTeam(), winner, "BYE match winner should be homeTeam");
    }
    
    @Test
    void testDetermineLoser_ByeMatch() {
        // BYE matches have no loser
        Match match = createMatch(1L);
        match.setIsBye(true);
        match.setHomeScore(0);
        match.setAwayScore(0);

        Team loser = service.determineLoser(match);

        assertNull(loser, "BYE match should have no loser");
    }
    
    @Test
    void testGenerateBracket_3Teams() {
        // 3 teams need a 4-team bracket with 1 BYE
        Tournament tournament = createTournament(1L, "Test Tournament");
        List<Team> teams = createTeams(3);

        when(matchRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Match> matches = invocation.getArgument(0);
            long id = 1L;
            for (Match m : matches) {
                m.setId(id++);
            }
            return matches;
        });

        // Act
        List<Match> matches = service.generateBracket(tournament, teams);

        // Assert
        verify(matchRepository, atLeastOnce()).saveAll(matchListCaptor.capture());
        List<Match> savedMatches = matchListCaptor.getAllValues().get(0);

        // 4-team bracket, first round = 2 matches (1 BYE + 1 real)
        assertEquals(2, savedMatches.size());
        
        // Count BYE matches
        long byeCount = savedMatches.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsBye()))
                .count();
        assertEquals(1, byeCount, "Should have 1 BYE match for 3 teams in 4-team bracket");
        
        // Count real matches
        long realCount = savedMatches.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsBye()))
                .count();
        assertEquals(1, realCount, "Should have 1 real match for 3 teams");
        
        // All should be SEMI_FINAL stage (first round for 3-4 teams)
        assertTrue(savedMatches.stream().allMatch(m -> m.getStage() == MatchStage.SEMI_FINAL),
                "All first-round matches should be SEMI_FINAL for 3-4 teams");
    }
}
