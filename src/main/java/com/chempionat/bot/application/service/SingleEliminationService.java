package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.enums.MatchStage;
import com.chempionat.bot.domain.enums.TournamentStatus;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.TeamRepository;
import com.chempionat.bot.domain.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Core service for single elimination (knockout/Olympic) tournament logic.
 * Handles bracket generation, stage detection, BYE handling, and winner propagation.
 */
@Slf4j
@Service
public class SingleEliminationService {

    private final MatchRepository matchRepository;
    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final NotificationService notificationService;

    public SingleEliminationService(
            MatchRepository matchRepository,
            TournamentRepository tournamentRepository,
            TeamRepository teamRepository,
            @Lazy NotificationService notificationService) {
        this.matchRepository = matchRepository;
        this.tournamentRepository = tournamentRepository;
        this.teamRepository = teamRepository;
        this.notificationService = notificationService;
    }

    /**
     * Stage order for bracket progression.
     * Index corresponds to the number of rounds remaining.
     */
    private static final MatchStage[] STAGE_ORDER = {
            MatchStage.FINAL,           // 0 rounds remaining (1 match)
            MatchStage.SEMI_FINAL,      // 1 round remaining (2 matches)
            MatchStage.QUARTER_FINAL,   // 2 rounds remaining (4 matches)
            MatchStage.ROUND_OF_16,     // 3 rounds remaining (8 matches)
            MatchStage.ROUND_OF_32,     // 4 rounds remaining (16 matches)
            MatchStage.ROUND_OF_64      // 5 rounds remaining (32 matches)
    };

    /**
     * Detect the starting stage based on team count.
     * Returns the appropriate stage for the first round of matches.
     */
    public MatchStage detectStartingStage(int teamCount) {
        int bracketSize = calculateBracketSize(teamCount);
        int numberOfRounds = (int) (Math.log(bracketSize) / Math.log(2));
        
        // numberOfRounds gives us index: 1->FINAL, 2->SF, 3->QF, 4->R16, 5->R32, 6->R64
        int stageIndex = Math.min(numberOfRounds - 1, STAGE_ORDER.length - 1);
        return STAGE_ORDER[stageIndex];
    }

    /**
     * Get display name for a stage in tournament context.
     */
    public String getStageDisplayName(MatchStage stage) {
        return switch (stage) {
            case ROUND_OF_64 -> "1/32";
            case ROUND_OF_32 -> "1/16";
            case ROUND_OF_16 -> "1/8";
            case QUARTER_FINAL -> "Chorak final";
            case SEMI_FINAL -> "Yarim final";
            case THIRD_PLACE -> "3-o'rin uchun";
            case FINAL -> "Final";
            default -> stage.name();
        };
    }

    /**
     * Calculate bracket size (next power of 2 >= teamCount).
     */
    public int calculateBracketSize(int teamCount) {
        if (teamCount <= 1) return 2;
        int power = 1;
        while (power < teamCount) {
            power *= 2;
        }
        return power;
    }

    /**
     * Generate bracket matches for a tournament.
     * Creates only first-round matches initially. Subsequent round matches
     * are created dynamically when winners advance.
     * Handles BYE assignments for odd team counts.
     */
    @Transactional
    public List<Match> generateBracket(Tournament tournament, List<Team> teams) {
        if (teams.size() < 2) {
            throw new IllegalArgumentException("At least 2 teams required for playoff");
        }

        int teamCount = teams.size();
        int bracketSize = calculateBracketSize(teamCount);
        int totalRounds = (int) (Math.log(bracketSize) / Math.log(2));
        int byeCount = bracketSize - teamCount;
        int firstRoundMatchCount = bracketSize / 2;

        log.info("Generating bracket: {} teams, bracket size {}, {} rounds, {} byes",
                teamCount, bracketSize, totalRounds, byeCount);

        // Shuffle teams for random seeding (can be replaced with seeding logic later)
        List<Team> shuffledTeams = new ArrayList<>(teams);
        Collections.shuffle(shuffledTeams);

        // Get starting stage based on team count
        MatchStage startingStage = detectStartingStage(teamCount);

        List<Match> firstRoundMatches = new ArrayList<>();
        int bracketPosition = 1;
        int teamIndex = 0;

        // Create first round matches
        for (int i = 0; i < firstRoundMatchCount; i++) {
            Match match;
            
            if (i < byeCount) {
                // This slot gets a BYE - only one team assigned, auto-advance
                Team team = shuffledTeams.get(teamIndex++);
                match = Match.builder()
                        .tournament(tournament)
                        .homeTeam(team)
                        .awayTeam(team) // Set both to same team for BYE
                        .state(MatchLifecycleState.APPROVED)
                        .round(1)
                        .stage(startingStage)
                        .bracketPosition(bracketPosition++)
                        .isBye(true)
                        .homeScore(0)
                        .awayScore(0)
                        .build();
            } else {
                // Normal match with two teams
                Team homeTeam = shuffledTeams.get(teamIndex++);
                Team awayTeam = shuffledTeams.get(teamIndex++);
                match = Match.builder()
                        .tournament(tournament)
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .state(MatchLifecycleState.CREATED)
                        .round(1)
                        .stage(startingStage)
                        .bracketPosition(bracketPosition++)
                        .isBye(false)
                        .build();
            }
            firstRoundMatches.add(match);
        }

        // Save first round matches
        List<Match> savedMatches = matchRepository.saveAll(firstRoundMatches);
        
        log.info("Generated {} first-round matches for tournament {}", savedMatches.size(), tournament.getId());

        // Handle BYE advancements - create next round matches for BYE winners
        List<Match> byeMatches = savedMatches.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsBye()))
                .toList();
        
        if (!byeMatches.isEmpty()) {
            processInitialByeAdvancements(tournament, byeMatches, savedMatches, totalRounds);
        }

        return savedMatches;
    }
    
    /**
     * Process BYE advancements - advance teams with BYEs to next round.
     */
    private void processInitialByeAdvancements(Tournament tournament, List<Match> byeMatches, 
                                                List<Match> allFirstRoundMatches, int totalRounds) {
        // Group matches into pairs and create next round match if both are ready
        for (int i = 0; i < allFirstRoundMatches.size(); i += 2) {
            Match match1 = allFirstRoundMatches.get(i);
            Match match2 = i + 1 < allFirstRoundMatches.size() ? allFirstRoundMatches.get(i + 1) : null;
            
            if (match2 == null) {
                // Odd number of matches, this winner advances directly
                continue;
            }
            
            boolean match1IsBye = Boolean.TRUE.equals(match1.getIsBye());
            boolean match2IsBye = Boolean.TRUE.equals(match2.getIsBye());
            
            if (match1IsBye && match2IsBye) {
                // Both are BYEs - create next round match immediately
                Team winner1 = match1.getHomeTeam();
                Team winner2 = match2.getHomeTeam();
                
                Match nextMatch = createNextRoundMatch(tournament, match1, winner1, winner2, totalRounds);
                match1.setNextMatch(nextMatch);
                match1.setWinnerToHome(true);
                match2.setNextMatch(nextMatch);
                match2.setWinnerToHome(false);
                
                matchRepository.saveAll(List.of(match1, match2));
            } else if (match1IsBye) {
                // Match1 is BYE, store winner to advance when match2 completes
                match1.setWinnerToHome(true);
                matchRepository.save(match1);
            } else if (match2IsBye) {
                // Match2 is BYE, store winner to advance when match1 completes
                match2.setWinnerToHome(false);
                matchRepository.save(match2);
            }
        }
    }
    
    /**
     * Create a match for the next round.
     */
    private Match createNextRoundMatch(Tournament tournament, Match previousMatch, 
                                        Team homeTeam, Team awayTeam, int totalRounds) {
        int currentRound = previousMatch.getRound();
        int nextRound = currentRound + 1;
        MatchStage nextStage = getNextStage(previousMatch.getStage());
        
        Match nextMatch = Match.builder()
                .tournament(tournament)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .state(MatchLifecycleState.CREATED)
                .round(nextRound)
                .stage(nextStage)
                .bracketPosition(previousMatch.getBracketPosition() / 2 + 1000 * nextRound)
                .isBye(false)
                .build();
        
        return matchRepository.save(nextMatch);
    }

    /**
     * Get the stage for a given round number.
     */
    private MatchStage getStageForRound(int round, int totalRounds) {
        int roundsFromFinal = round - 1; // 0 = final, 1 = semi, etc.
        if (roundsFromFinal < STAGE_ORDER.length) {
            return STAGE_ORDER[roundsFromFinal];
        }
        return MatchStage.ROUND_OF_64; // Fallback for very large brackets
    }
    
    /**
     * Get the next stage after the current one.
     */
    private MatchStage getNextStage(MatchStage currentStage) {
        return switch (currentStage) {
            case ROUND_OF_64 -> MatchStage.ROUND_OF_32;
            case ROUND_OF_32 -> MatchStage.ROUND_OF_16;
            case ROUND_OF_16 -> MatchStage.QUARTER_FINAL;
            case QUARTER_FINAL -> MatchStage.SEMI_FINAL;
            case SEMI_FINAL -> MatchStage.FINAL;
            case FINAL -> MatchStage.FINAL; // Final is the last stage
            default -> MatchStage.FINAL;
        };
    }

    /**
     * Propagate winner to the next match after a match is completed.
     * Called after a match result is approved.
     */
    @Transactional
    public void propagateWinner(Match match) {
        if (match.getHomeScore() == null || match.getAwayScore() == null) {
            log.warn("Cannot propagate winner: match {} has no scores", match.getId());
            return;
        }

        Team winner = determineWinner(match);
        if (winner == null) {
            log.warn("Cannot determine winner for match {}: draw not allowed in playoff", match.getId());
            return;
        }

        propagateWinnerInternal(match, winner);
        
        // Check if tournament is complete
        checkTournamentCompletion(match.getTournament());
    }

    /**
     * Internal method to propagate winner.
     * Creates next round match dynamically if needed.
     * Thread-safe: Uses pessimistic locking to prevent duplicate next-match creation.
     */
    private void propagateWinnerInternal(Match match, Team winner) {
        Long matchId = match.getId();
        Long tournamentId = match.getTournament().getId();
        Integer currentRound = match.getRound();
        
        // Check if this is the final
        if (match.getStage() == MatchStage.FINAL) {
            log.info("PROPAGATE_FINAL: matchId={}, tournamentId={}, winner={} - this is the final, no propagation needed", 
                    matchId, tournamentId, winner.getName());
            return;
        }
        
        Match nextMatch = match.getNextMatch();
        
        if (nextMatch != null) {
            // Next match already exists, just assign winner with lock
            Match lockedNextMatch = matchRepository.findByIdWithLock(nextMatch.getId())
                    .orElse(null);
            
            if (lockedNextMatch != null) {
                if (Boolean.TRUE.equals(match.getWinnerToHome())) {
                    lockedNextMatch.setHomeTeam(winner);
                } else {
                    lockedNextMatch.setAwayTeam(winner);
                }
                matchRepository.save(lockedNextMatch);
                log.info("PROPAGATE_EXISTING: matchId={}, tournamentId={}, round={}, winner={}, nextMatchId={}, slot={}", 
                        matchId, tournamentId, currentRound, winner.getName(), lockedNextMatch.getId(),
                        match.getWinnerToHome() ? "home" : "away");
            }
        } else {
            // Need to find or create next round match
            // CRITICAL: Use pessimistic lock on round matches to prevent race conditions
            Tournament tournament = match.getTournament();
            List<Match> roundMatches = matchRepository.findByTournamentAndRoundWithLock(tournament, currentRound);
            
            // Sort by bracket position to find pairs
            roundMatches.sort(Comparator.comparing(m -> m.getBracketPosition() != null ? m.getBracketPosition() : 0));
            
            int myIndex = -1;
            for (int i = 0; i < roundMatches.size(); i++) {
                if (roundMatches.get(i).getId().equals(matchId)) {
                    myIndex = i;
                    break;
                }
            }
            
            if (myIndex == -1) {
                log.error("PROPAGATE_ERROR: matchId={}, tournamentId={} - match not found in round matches", matchId, tournamentId);
                return;
            }
            
            int partnerIndex = (myIndex % 2 == 0) ? myIndex + 1 : myIndex - 1;
            
            if (partnerIndex >= 0 && partnerIndex < roundMatches.size()) {
                Match partnerMatch = roundMatches.get(partnerIndex);
                
                // DOUBLE-CHECK: Verify our match still has no nextMatch after acquiring lock
                // (might have been created by concurrent partner approval)
                Match refreshedMatch = matchRepository.findByIdWithLock(matchId).orElse(null);
                if (refreshedMatch != null && refreshedMatch.getNextMatch() != null) {
                    // Next match was created by concurrent partner - just assign our winner
                    Match existingNextMatch = matchRepository.findByIdWithLock(refreshedMatch.getNextMatch().getId()).orElse(null);
                    if (existingNextMatch != null) {
                        if (Boolean.TRUE.equals(refreshedMatch.getWinnerToHome())) {
                            existingNextMatch.setHomeTeam(winner);
                        } else {
                            existingNextMatch.setAwayTeam(winner);
                        }
                        matchRepository.save(existingNextMatch);
                        log.info("PROPAGATE_RACE_RESOLVED: matchId={}, tournamentId={}, round={}, winner={}, nextMatchId={} - joined existing next match", 
                                matchId, tournamentId, currentRound, winner.getName(), existingNextMatch.getId());
                    }
                    return;
                }
                
                // Check if partner match is complete (has approved state)
                boolean partnerComplete = partnerMatch.getState() == MatchLifecycleState.APPROVED &&
                        partnerMatch.getHomeScore() != null && partnerMatch.getAwayScore() != null;
                
                if (partnerComplete) {
                    Team partnerWinner = determineWinner(partnerMatch);
                    
                    if (partnerWinner == null) {
                        log.warn("PROPAGATE_WARN: matchId={}, partnerId={}, tournamentId={} - cannot determine partner winner", 
                                matchId, partnerMatch.getId(), tournamentId);
                        return;
                    }
                    
                    // CHECK FOR EXISTING NEXT MATCH: Maybe partner already created it
                    if (partnerMatch.getNextMatch() != null) {
                        // Partner already created next match, just join it
                        Match existingNextMatch = matchRepository.findByIdWithLock(partnerMatch.getNextMatch().getId()).orElse(null);
                        if (existingNextMatch != null) {
                            if (myIndex % 2 == 0) {
                                existingNextMatch.setHomeTeam(winner);
                            } else {
                                existingNextMatch.setAwayTeam(winner);
                            }
                            match.setNextMatch(existingNextMatch);
                            match.setWinnerToHome(myIndex % 2 == 0);
                            matchRepository.saveAll(List.of(match, existingNextMatch));
                            log.info("PROPAGATE_JOINED: matchId={}, tournamentId={}, round={}, winner={}, existingNextMatchId={}", 
                                    matchId, tournamentId, currentRound, winner.getName(), existingNextMatch.getId());
                            notifyAdvancement(winner, match, existingNextMatch);
                            return;
                        }
                    }
                    
                    // Both matches complete and no existing next match - create new one
                    Team homeTeam, awayTeam;
                    if (myIndex % 2 == 0) {
                        homeTeam = winner;
                        awayTeam = partnerWinner;
                    } else {
                        homeTeam = partnerWinner;
                        awayTeam = winner;
                    }
                    
                    int teamCount = (int) teamRepository.countByTournament(tournament);
                    int totalRounds = (int) Math.ceil(Math.log(calculateBracketSize(teamCount)) / Math.log(2));
                    
                    nextMatch = createNextRoundMatch(tournament, match, homeTeam, awayTeam, totalRounds);
                    
                    // Link both matches to the new next match
                    match.setNextMatch(nextMatch);
                    match.setWinnerToHome(myIndex % 2 == 0);
                    partnerMatch.setNextMatch(nextMatch);
                    partnerMatch.setWinnerToHome(myIndex % 2 != 0);
                    
                    matchRepository.saveAll(List.of(match, partnerMatch));
                    
                    log.info("PROPAGATE_CREATED: matchId={}, partnerId={}, tournamentId={}, round={}, nextMatchId={}, homeWinner={}, awayWinner={}", 
                            matchId, partnerMatch.getId(), tournamentId, currentRound, nextMatch.getId(), 
                            homeTeam.getName(), awayTeam.getName());
                } else {
                    // Partner not done yet, just save our result
                    log.info("PROPAGATE_WAITING: matchId={}, tournamentId={}, round={}, partnerId={}, partnerState={}", 
                            matchId, tournamentId, currentRound, partnerMatch.getId(), partnerMatch.getState());
                }
            } else {
                log.warn("PROPAGATE_ODD: matchId={}, tournamentId={}, round={}, myIndex={} - no partner match found (odd bracket?)", 
                        matchId, tournamentId, currentRound, myIndex);
            }
        }

        // Notify winner about advancement
        notifyAdvancement(winner, match, nextMatch);
    }

    /**
     * Determine winner of a match.
     * For BYE matches, returns the home team (the only real participant).
     * For draws in knockout, checks penalty scores to determine winner.
     */
    public Team determineWinner(Match match) {
        // For BYE matches, home team is the winner
        if (Boolean.TRUE.equals(match.getIsBye())) {
            return match.getHomeTeam();
        }
        
        if (match.getHomeScore() == null || match.getAwayScore() == null) {
            return null;
        }
        
        // Normal time: clear winner
        if (match.getHomeScore() > match.getAwayScore()) {
            return match.getHomeTeam();
        } else if (match.getAwayScore() > match.getHomeScore()) {
            return match.getAwayTeam();
        }
        
        // Draw in normal time - check penalty shootout results
        if (Boolean.TRUE.equals(match.getDecidedByPenalties()) 
                && match.getHomePenaltyScore() != null 
                && match.getAwayPenaltyScore() != null) {
            if (match.getHomePenaltyScore() > match.getAwayPenaltyScore()) {
                log.info("PENALTY_WINNER: matchId={}, winner=home, penalties={}:{}", 
                        match.getId(), match.getHomePenaltyScore(), match.getAwayPenaltyScore());
                return match.getHomeTeam();
            } else if (match.getAwayPenaltyScore() > match.getHomePenaltyScore()) {
                log.info("PENALTY_WINNER: matchId={}, winner=away, penalties={}:{}", 
                        match.getId(), match.getHomePenaltyScore(), match.getAwayPenaltyScore());
                return match.getAwayTeam();
            }
            // Penalties also tied - should not happen, but return null
            log.error("PENALTY_DRAW: matchId={} - penalties also tied {}:{}, cannot determine winner", 
                    match.getId(), match.getHomePenaltyScore(), match.getAwayPenaltyScore());
            return null;
        }
        
        // Draw without penalty result - needs penalty shootout
        log.debug("MATCH_DRAW: matchId={}, score={}:{} - awaiting penalty shootout", 
                match.getId(), match.getHomeScore(), match.getAwayScore());
        return null;
    }

    /**
     * Determine loser of a match.
     * For BYE matches, returns null (no loser).
     * For draws in knockout, checks penalty scores to determine loser.
     */
    public Team determineLoser(Match match) {
        // BYE matches have no loser
        if (Boolean.TRUE.equals(match.getIsBye())) {
            return null;
        }
        
        if (match.getHomeScore() == null || match.getAwayScore() == null) {
            return null;
        }
        
        // Normal time: clear loser
        if (match.getHomeScore() > match.getAwayScore()) {
            return match.getAwayTeam();
        } else if (match.getAwayScore() > match.getHomeScore()) {
            return match.getHomeTeam();
        }
        
        // Draw in normal time - check penalty shootout results
        if (Boolean.TRUE.equals(match.getDecidedByPenalties()) 
                && match.getHomePenaltyScore() != null 
                && match.getAwayPenaltyScore() != null) {
            if (match.getHomePenaltyScore() > match.getAwayPenaltyScore()) {
                return match.getAwayTeam(); // Away team lost on penalties
            } else if (match.getAwayPenaltyScore() > match.getHomePenaltyScore()) {
                return match.getHomeTeam(); // Home team lost on penalties
            }
        }
        
        return null; // Draw without penalty resolution
    }

    /**
     * Check if a match ended in a draw (requires penalty shootout in knockout).
     */
    public boolean isDrawMatch(Match match) {
        if (match.getHomeScore() == null || match.getAwayScore() == null) {
            return false;
        }
        return match.getHomeScore().equals(match.getAwayScore());
    }

    /**
     * Check if match needs penalty shootout (draw in knockout without penalty result).
     */
    public boolean needsPenaltyShootout(Match match, Tournament tournament) {
        if (tournament.getType() != TournamentType.PLAYOFF) {
            return false; // Only knockout tournaments need penalties
        }
        if (!isDrawMatch(match)) {
            return false; // Not a draw
        }
        // Check if penalty result already submitted
        return !Boolean.TRUE.equals(match.getDecidedByPenalties());
    }

    /**
     * Create third-place match from semifinal losers.
     * Called when both semifinals are complete.
     * 
     * @param tournament The tournament
     * @param semifinalMatches List of semifinal matches (should be exactly 2)
     * @return The created third-place match, or null if already exists or cannot create
     */
    @Transactional
    public Match createThirdPlaceMatch(Tournament tournament, List<Match> semifinalMatches) {
        if (semifinalMatches.size() != 2) {
            log.warn("THIRD_PLACE_SKIP: tournamentId={} - expected 2 semifinals, got {}", 
                    tournament.getId(), semifinalMatches.size());
            return null;
        }

        Match semi1 = semifinalMatches.get(0);
        Match semi2 = semifinalMatches.get(1);

        // Check if both semifinals are complete
        if (semi1.getState() != MatchLifecycleState.APPROVED || 
            semi2.getState() != MatchLifecycleState.APPROVED) {
            log.debug("THIRD_PLACE_WAIT: tournamentId={} - semifinals not both approved yet", tournament.getId());
            return null;
        }

        // Check if third-place match already exists
        List<Match> existingThirdPlace = matchRepository.findByTournamentAndStage(tournament, MatchStage.THIRD_PLACE);
        if (!existingThirdPlace.isEmpty()) {
            log.debug("THIRD_PLACE_EXISTS: tournamentId={} - already created", tournament.getId());
            return existingThirdPlace.get(0);
        }

        // Get losers from semifinals
        Team loser1 = determineLoser(semi1);
        Team loser2 = determineLoser(semi2);

        if (loser1 == null || loser2 == null) {
            log.error("THIRD_PLACE_ERROR: tournamentId={} - cannot determine semifinal losers (draw without penalties?)", 
                    tournament.getId());
            return null;
        }

        // Create third-place match
        Match thirdPlaceMatch = Match.builder()
                .tournament(tournament)
                .homeTeam(loser1)
                .awayTeam(loser2)
                .state(MatchLifecycleState.CREATED)
                .round(semi1.getRound() + 1) // Same round as final
                .stage(MatchStage.THIRD_PLACE)
                .bracketPosition(9999) // Special position for 3rd place
                .isBye(false)
                .isThirdPlaceMatch(true)
                .build();

        Match saved = matchRepository.save(thirdPlaceMatch);
        log.info("THIRD_PLACE_CREATED: tournamentId={}, matchId={}, teams={}vs{}", 
                tournament.getId(), saved.getId(), loser1.getName(), loser2.getName());

        // Notify both teams about third-place match
        notifyThirdPlaceMatch(saved, loser1, loser2);

        return saved;
    }

    /**
     * Check and create third-place match if both semifinals are complete.
     * Called after semifinal approval.
     * 
     * NOTE: Third-place match requires exactly 2 real (non-BYE) semifinals.
     * For 3-player tournaments with 1 BYE semifinal:
     *   - No third-place match is created
     *   - The semifinal loser is automatically assigned 3rd place
     */
    @Transactional
    public void checkAndCreateThirdPlaceMatch(Match completedSemifinal) {
        if (completedSemifinal.getStage() != MatchStage.SEMI_FINAL) {
            return; // Only handle semifinals
        }

        Tournament tournament = completedSemifinal.getTournament();
        
        // Get all semifinals for this tournament
        List<Match> semifinals = matchRepository.findByTournamentAndStage(tournament, MatchStage.SEMI_FINAL);
        
        // Filter to only include regular semifinals (not BYE matches)
        List<Match> realSemifinals = semifinals.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsBye()))
                .toList();

        // For 3-player tournaments, there's only 1 real semifinal
        // The semifinal loser is automatically 3rd place (no 3rd-place match needed)
        if (realSemifinals.size() == 1) {
            Match onlySemifinal = realSemifinals.get(0);
            if (onlySemifinal.getState() == MatchLifecycleState.APPROVED) {
                Team thirdPlace = determineLoser(onlySemifinal);
                if (thirdPlace != null) {
                    log.info("THIRD_PLACE_AUTO: tournamentId={} - auto-assigning 3rd place to {} (only 1 semifinal, no 3rd-place match)",
                            tournament.getId(), thirdPlace.getName());
                    
                    // Notify the team about their automatic 3rd place
                    notifyAutomaticThirdPlace(tournament, thirdPlace);
                }
            }
            return;
        }

        // For < 2 real semifinals (shouldn't happen in normal flow)
        if (realSemifinals.size() < 2) {
            log.info("THIRD_PLACE_SKIP: tournamentId={} - only {} real semifinal(s), need 2 for third-place match",
                    tournament.getId(), realSemifinals.size());
            return;
        }

        // Check if all real semifinals are complete
        boolean allComplete = realSemifinals.stream()
                .allMatch(m -> m.getState() == MatchLifecycleState.APPROVED);

        if (allComplete && realSemifinals.size() == 2) {
            createThirdPlaceMatch(tournament, realSemifinals);
        }
    }
    
    /**
     * Notify a team that they've been automatically assigned 3rd place.
     * This happens in 3-player tournaments where no 3rd-place match is needed.
     */
    private void notifyAutomaticThirdPlace(Tournament tournament, Team team) {
        if (team == null || team.getUser() == null || team.getUser().getTelegramId() == null) {
            return;
        }
        
        String message = String.format(
                "🥉 Tabriklaymiz! Siz avtomatik ravishda 3-o'rinni egallangiz!\n\n" +
                "🏆 %s\n\n" +
                "Turnirda ishtirok etganingiz uchun rahmat! 🎉",
                tournament.getName()
        );
        
        try {
            notificationService.notifyUser(team.getUser().getTelegramId(), message);
        } catch (Exception e) {
            log.error("Failed to notify team {} about automatic 3rd place", team.getName(), e);
        }
    }

    /**
     * Notify teams about third-place match.
     */
    private void notifyThirdPlaceMatch(Match match, Team team1, Team team2) {
        String message = String.format(
                "🏆 3-o'rin uchun o'yin tayinlandi!\n\n" +
                "🏠 %s\n" +
                "✈️ %s\n\n" +
                "Match ID: %d\n" +
                "Bu o'yinda g'olib 3-o'rinni egallaydi!",
                team1.getName(), team2.getName(), match.getId()
        );

        try {
            if (team1.getUser() != null && team1.getUser().getTelegramId() != null) {
                notificationService.notifyUser(team1.getUser().getTelegramId(), message);
            }
            if (team2.getUser() != null && team2.getUser().getTelegramId() != null) {
                notificationService.notifyUser(team2.getUser().getTelegramId(), message);
            }
        } catch (Exception e) {
            log.error("Failed to notify teams about third-place match {}", match.getId(), e);
        }
    }
    
    /**
     * Check if a third-place match exists for this tournament.
     * Used to determine if semifinal losers should be notified of elimination.
     */
    public boolean hasThirdPlaceMatch(Tournament tournament) {
        List<Match> thirdPlaceMatches = matchRepository.findByTournamentAndStage(tournament, MatchStage.THIRD_PLACE);
        return !thirdPlaceMatches.isEmpty();
    }
    
    /**
     * Check if third-place match (if exists) is complete.
     * Returns true if:
     *   - No third-place match exists (e.g., 3-player tournament)
     *   - Third-place match is APPROVED
     */
    private boolean isThirdPlaceComplete(Tournament tournament) {
        List<Match> thirdPlaceMatches = matchRepository.findByTournamentAndStage(tournament, MatchStage.THIRD_PLACE);
        if (thirdPlaceMatches.isEmpty()) {
            return true; // No third-place match needed
        }
        
        Match thirdPlace = thirdPlaceMatches.get(0);
        return thirdPlace.getState() == MatchLifecycleState.APPROVED;
    }

    /**
     * Check if tournament is complete.
     * 
     * Tournament is complete when:
     *   - Final match is APPROVED
     *   - Third-place match (if exists) is APPROVED
     * 
     * For 3-player tournaments: third-place is auto-assigned when semifinal completes,
     * so we only need to wait for the final.
     */
    @Transactional
    public void checkTournamentCompletion(Tournament tournament) {
        if (tournament.getType() != TournamentType.PLAYOFF) {
            return;
        }

        Optional<Match> finalMatch = matchRepository.findFinalMatch(tournament);
        if (finalMatch.isEmpty()) {
            return;
        }

        Match final_ = finalMatch.get();
        boolean finalComplete = final_.getHomeScore() != null 
                && final_.getAwayScore() != null 
                && final_.getState() == MatchLifecycleState.APPROVED;
        
        if (!finalComplete) {
            return; // Final not done yet
        }
        
        // Check if third-place match is complete (or doesn't exist)
        boolean thirdPlaceComplete = isThirdPlaceComplete(tournament);
        
        if (!thirdPlaceComplete) {
            log.info("TOURNAMENT_WAIT_THIRD_PLACE: tournamentId={} - final complete, waiting for third-place match", 
                    tournament.getId());
            return; // Third-place match still pending
        }
        
        // Tournament is fully complete (final + third-place if applicable)
        tournament.setStatus(TournamentStatus.FINISHED);
        tournament.setIsActive(false);
        tournamentRepository.save(tournament);
        
        Team winner = determineWinner(final_);
        log.info("TOURNAMENT_COMPLETE: tournamentId={}, winner={}", 
                tournament.getId(), winner != null ? winner.getName() : "Unknown");

        // Notify about tournament completion
        notifyTournamentCompletion(tournament, final_, winner);
    }

    /**
     * Handle disqualification of a team - auto-advance opponent.
     */
    @Transactional
    public void disqualifyTeam(Match match, Team disqualifiedTeam) {
        Team opponent;
        if (match.getHomeTeam().getId().equals(disqualifiedTeam.getId())) {
            opponent = match.getAwayTeam();
            match.setHomeScore(0);
            match.setAwayScore(3); // Walkover score
        } else {
            opponent = match.getHomeTeam();
            match.setHomeScore(3);
            match.setAwayScore(0);
        }

        match.setState(MatchLifecycleState.APPROVED);
        matchRepository.save(match);

        propagateWinnerInternal(match, opponent);
        
        log.info("Team {} disqualified from match {}. {} advances.", 
                disqualifiedTeam.getName(), match.getId(), opponent.getName());
    }

    /**
     * Handle walkover - one team doesn't show up.
     */
    @Transactional
    public void recordWalkover(Match match, Team absentTeam) {
        disqualifyTeam(match, absentTeam);
    }

    /**
     * Get bracket tree for visualization.
     * Returns matches ordered by bracket position.
     */
    public List<Match> getBracketTree(Tournament tournament) {
        return matchRepository.findByTournamentOrderedByBracketPosition(tournament);
    }

    /**
     * Get matches by stage.
     */
    public List<Match> getMatchesByStage(Tournament tournament, MatchStage stage) {
        return matchRepository.findByTournamentAndStage(tournament, stage);
    }

    /**
     * Notify team about advancement to next round.
     */
    private void notifyAdvancement(Team winner, Match completedMatch, Match nextMatch) {
        if (winner == null || winner.getUser() == null || winner.getUser().getTelegramId() == null) {
            return;
        }
        
        // If nextMatch is null, partner is not done yet - don't notify
        if (nextMatch == null) {
            log.debug("Skipping advancement notification for {} - waiting for partner match", winner.getName());
            return;
        }

        String stageName = getStageDisplayName(nextMatch.getStage());
        String message = String.format(
                "🎉 Tabriklaymiz! Siz keyingi bosqichga o'tdingiz!\n\n" +
                "🏆 %s\n" +
                "📍 Keyingi bosqich: %s\n\n" +
                "Raqibingiz haqida tez orada xabar beriladi.",
                completedMatch.getTournament().getName(),
                stageName
        );

        try {
            notificationService.notifyUser(winner.getUser().getTelegramId(), message);
        } catch (Exception e) {
            log.error("Failed to notify user {} about advancement", winner.getUser().getTelegramId(), e);
        }
    }

    /**
     * Notify eliminated team.
     */
    public void notifyElimination(Team loser, Match match) {
        if (loser == null || loser.getUser() == null || loser.getUser().getTelegramId() == null) {
            return;
        }

        String stageName = getStageDisplayName(match.getStage());
        String message = String.format(
                "😔 Afsuski, siz turnirdan chiqib ketdingiz.\n\n" +
                "🏆 %s\n" +
                "📍 Bosqich: %s\n\n" +
                "Kelasi turnirda omad! 🍀",
                match.getTournament().getName(),
                stageName
        );

        try {
            notificationService.notifyUser(loser.getUser().getTelegramId(), message);
        } catch (Exception e) {
            log.error("Failed to notify user {} about elimination", loser.getUser().getTelegramId(), e);
        }
    }

    /**
     * Notify about tournament completion - 🏆 for winner, 🥈 for runner-up.
     * Includes penalty information if final was decided by penalties.
     */
    private void notifyTournamentCompletion(Tournament tournament, Match finalMatch, Team winner) {
        Team runnerUp = determineLoser(finalMatch);
        
        // Build score string with penalty info
        String scoreStr = buildFinalScoreString(finalMatch);

        // Notify 🏆 winner
        if (winner != null && winner.getUser() != null && winner.getUser().getTelegramId() != null) {
            String winnerMessage = String.format(
                    "🏆🎊 TABRIKLAYMIZ! SIZ G'OLIB BO'LDINGIZ! 🎊🏆\n\n" +
                    "🏆 %s\n\n" +
                    "Final natijasi:\n%s\n\n" +
                    "Ajoyib g'alaba! 🌟",
                    tournament.getName(), scoreStr
            );
            try {
                notificationService.notifyUser(winner.getUser().getTelegramId(), winnerMessage);
            } catch (Exception e) {
                log.error("Failed to notify winner", e);
            }
        }

        // Notify 🥈 runner-up
        if (runnerUp != null && runnerUp.getUser() != null && runnerUp.getUser().getTelegramId() != null) {
            String runnerUpMessage = String.format(
                    "🥈 Tabriklaymiz! Siz 2-o'rinni egallangiz!\n\n" +
                    "🏆 %s\n\n" +
                    "Final natijasi:\n%s\n\n" +
                    "Finalda 2-o'rin - bu ham ajoyib natija! 🌟",
                    tournament.getName(), scoreStr
            );
            try {
                notificationService.notifyUser(runnerUp.getUser().getTelegramId(), runnerUpMessage);
            } catch (Exception e) {
                log.error("Failed to notify runner-up", e);
            }
        }
        
        log.info("TOURNAMENT_COMPLETE: tournamentId={}, winner={}, runnerUp={}", 
                tournament.getId(), 
                winner != null ? winner.getName() : "null",
                runnerUp != null ? runnerUp.getName() : "null");
    }
    
    /**
     * Build final score string with penalty info for notifications.
     */
    private String buildFinalScoreString(Match match) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("🏠 %s: %d", match.getHomeTeam().getName(), match.getHomeScore()));
        if (Boolean.TRUE.equals(match.getDecidedByPenalties()) && match.getHomePenaltyScore() != null) {
            sb.append(String.format(" (%d)", match.getHomePenaltyScore()));
        }
        sb.append("\n");
        sb.append(String.format("✈️ %s: %d", match.getAwayTeam().getName(), match.getAwayScore()));
        if (Boolean.TRUE.equals(match.getDecidedByPenalties()) && match.getAwayPenaltyScore() != null) {
            sb.append(String.format(" (%d)", match.getAwayPenaltyScore()));
        }
        if (Boolean.TRUE.equals(match.getDecidedByPenalties())) {
            sb.append("\n🎯 Penaltilar bilan hal bo'ldi");
        }
        return sb.toString();
    }

    /**
     * Cancel a tournament.
     */
    @Transactional
    public void cancelTournament(Tournament tournament) {
        tournament.setStatus(TournamentStatus.CANCELLED);
        tournament.setIsActive(false);
        tournamentRepository.save(tournament);
        
        log.info("Tournament {} cancelled", tournament.getId());
    }

    /**
     * Check if a playoff match result has a draw (not allowed).
     */
    public boolean isDraw(Integer homeScore, Integer awayScore) {
        return homeScore != null && awayScore != null && homeScore.equals(awayScore);
    }
    
    /**
     * Calculate bracket-based placements for a single elimination tournament.
     * Returns teams in order: 1st (winner), 2nd (runner-up), 3rd (semifinal loser), etc.
     * This is NOT points-based - it's purely based on bracket progression.
     */
    public List<TeamStanding> calculateBracketPlacements(Tournament tournament) {
        List<Team> allTeams = teamRepository.findByTournament(tournament);
        List<Match> allMatches = matchRepository.findByTournament(tournament);
        
        List<TeamStanding> placements = new ArrayList<>();
        
        // Check if final exists and is complete
        Optional<Match> finalMatchOpt = matchRepository.findFinalMatch(tournament);
        
        if (finalMatchOpt.isPresent()) {
            Match finalMatch = finalMatchOpt.get();
            
            if (finalMatch.getHomeScore() != null && finalMatch.getAwayScore() != null) {
                // 1st place - Final winner
                Team winner = determineWinner(finalMatch);
                if (winner != null) {
                    TeamStanding first = new TeamStanding(winner.getId(), winner.getName());
                    first.setPosition(1);
                    placements.add(first);
                }
                
                // 2nd place - Final loser
                Team runnerUp = determineLoser(finalMatch);
                if (runnerUp != null) {
                    TeamStanding second = new TeamStanding(runnerUp.getId(), runnerUp.getName());
                    second.setPosition(2);
                    placements.add(second);
                }
            }
        }
        
        // Find semifinal losers (3rd/4th place)
        List<Match> semifinals = matchRepository.findByTournamentAndStage(tournament, MatchStage.SEMI_FINAL);
        List<Team> semifinalLosers = new ArrayList<>();
        
        for (Match sf : semifinals) {
            if (sf.getHomeScore() != null && sf.getAwayScore() != null 
                    && !Boolean.TRUE.equals(sf.getIsBye())) {
                Team loser = determineLoser(sf);
                if (loser != null) {
                    semifinalLosers.add(loser);
                }
            }
        }
        
        // Add semifinal losers as 3rd/4th place
        int position = 3;
        for (Team loser : semifinalLosers) {
            TeamStanding standing = new TeamStanding(loser.getId(), loser.getName());
            standing.setPosition(position++);
            placements.add(standing);
        }
        
        // Find quarterfinal losers (5th-8th place)
        List<Match> quarterFinals = matchRepository.findByTournamentAndStage(tournament, MatchStage.QUARTER_FINAL);
        List<Team> qfLosers = new ArrayList<>();
        
        for (Match qf : quarterFinals) {
            if (qf.getHomeScore() != null && qf.getAwayScore() != null 
                    && !Boolean.TRUE.equals(qf.getIsBye())) {
                Team loser = determineLoser(qf);
                if (loser != null && !isTeamAlreadyPlaced(placements, loser)) {
                    qfLosers.add(loser);
                }
            }
        }
        
        for (Team loser : qfLosers) {
            TeamStanding standing = new TeamStanding(loser.getId(), loser.getName());
            standing.setPosition(position++);
            placements.add(standing);
        }
        
        // Add remaining teams who lost in earlier rounds
        Set<Long> placedTeamIds = placements.stream()
                .map(TeamStanding::getTeamId)
                .collect(java.util.stream.Collectors.toSet());
        
        for (Team team : allTeams) {
            if (!placedTeamIds.contains(team.getId())) {
                TeamStanding standing = new TeamStanding(team.getId(), team.getName());
                standing.setPosition(position++);
                placements.add(standing);
            }
        }
        
        return placements;
    }
    
    private boolean isTeamAlreadyPlaced(List<TeamStanding> placements, Team team) {
        return placements.stream().anyMatch(p -> p.getTeamId().equals(team.getId()));
    }
}
