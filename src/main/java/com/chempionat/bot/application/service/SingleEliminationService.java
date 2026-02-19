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
     */
    private void propagateWinnerInternal(Match match, Team winner) {
        // Check if this is the final
        if (match.getStage() == MatchStage.FINAL) {
            log.info("Match {} is the final - winner: {}", match.getId(), winner.getName());
            return;
        }
        
        Match nextMatch = match.getNextMatch();
        
        if (nextMatch != null) {
            // Next match already exists, just assign winner
            if (Boolean.TRUE.equals(match.getWinnerToHome())) {
                nextMatch.setHomeTeam(winner);
            } else {
                nextMatch.setAwayTeam(winner);
            }
            matchRepository.save(nextMatch);
            log.info("Winner {} advanced from match {} to existing match {} ({})",
                    winner.getName(), match.getId(), nextMatch.getId(),
                    match.getWinnerToHome() ? "home" : "away");
        } else {
            // Need to find or create next round match
            // Find partner match (the other first-round match that feeds into same next match)
            Tournament tournament = match.getTournament();
            List<Match> roundMatches = matchRepository.findByTournamentAndRound(tournament, match.getRound());
            
            // Sort by bracket position to find pairs
            roundMatches.sort(Comparator.comparing(m -> m.getBracketPosition() != null ? m.getBracketPosition() : 0));
            
            int myIndex = roundMatches.indexOf(match);
            int partnerIndex = (myIndex % 2 == 0) ? myIndex + 1 : myIndex - 1;
            
            if (partnerIndex >= 0 && partnerIndex < roundMatches.size()) {
                Match partnerMatch = roundMatches.get(partnerIndex);
                
                // Check if partner match is complete
                if (partnerMatch.getHomeScore() != null && partnerMatch.getAwayScore() != null) {
                    Team partnerWinner = determineWinner(partnerMatch);
                    
                    // Both matches complete, create next round match
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
                    
                    log.info("Created next round match {} for winners {} and {}",
                            nextMatch.getId(), winner.getName(), partnerWinner.getName());
                } else {
                    // Partner not done yet, just save our result
                    log.info("Match {} complete, waiting for partner match {} to complete",
                            match.getId(), partnerMatch.getId());
                }
            }
        }

        // Notify winner about advancement
        notifyAdvancement(winner, match, nextMatch);
    }

    /**
     * Determine winner of a match.
     * For BYE matches, returns the home team (the only real participant).
     */
    public Team determineWinner(Match match) {
        // For BYE matches, home team is the winner
        if (Boolean.TRUE.equals(match.getIsBye())) {
            return match.getHomeTeam();
        }
        
        if (match.getHomeScore() == null || match.getAwayScore() == null) {
            return null;
        }
        if (match.getHomeScore() > match.getAwayScore()) {
            return match.getHomeTeam();
        } else if (match.getAwayScore() > match.getHomeScore()) {
            return match.getAwayTeam();
        }
        return null; // Draw - should not happen in playoff
    }

    /**
     * Determine loser of a match.
     * For BYE matches, returns null (no loser).
     */
    public Team determineLoser(Match match) {
        // BYE matches have no loser
        if (Boolean.TRUE.equals(match.getIsBye())) {
            return null;
        }
        
        if (match.getHomeScore() == null || match.getAwayScore() == null) {
            return null;
        }
        if (match.getHomeScore() > match.getAwayScore()) {
            return match.getAwayTeam();
        } else if (match.getAwayScore() > match.getHomeScore()) {
            return match.getHomeTeam();
        }
        return null; // Draw
    }

    /**
     * Check if tournament is complete (final match finished).
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
        if (final_.getHomeScore() != null && final_.getAwayScore() != null 
                && final_.getState() == MatchLifecycleState.APPROVED) {
            
            // Tournament is complete!
            tournament.setStatus(TournamentStatus.FINISHED);
            tournament.setIsActive(false);
            tournamentRepository.save(tournament);
            
            Team winner = determineWinner(final_);
            log.info("Tournament {} completed! Winner: {}", tournament.getId(), 
                    winner != null ? winner.getName() : "Unknown");

            // Notify about tournament completion
            notifyTournamentCompletion(tournament, final_, winner);
        }
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
        if (winner.getUser() == null || winner.getUser().getTelegramId() == null) {
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
     * Notify about tournament completion.
     */
    private void notifyTournamentCompletion(Tournament tournament, Match finalMatch, Team winner) {
        Team runnerUp = determineLoser(finalMatch);

        // Notify winner
        if (winner != null && winner.getUser() != null && winner.getUser().getTelegramId() != null) {
            String winnerMessage = String.format(
                    "🏆🎊 TABRIKLAYMIZ! SIZ G'OLIB BO'LDINGIZ! 🎊🏆\n\n" +
                    "🏆 %s\n\n" +
                    "Final natijasi:\n" +
                    "🏠 %s: %d\n" +
                    "✈️ %s: %d\n\n" +
                    "Ajoyib g'alaba! 🌟",
                    tournament.getName(),
                    finalMatch.getHomeTeam().getName(), finalMatch.getHomeScore(),
                    finalMatch.getAwayTeam().getName(), finalMatch.getAwayScore()
            );
            try {
                notificationService.notifyUser(winner.getUser().getTelegramId(), winnerMessage);
            } catch (Exception e) {
                log.error("Failed to notify winner", e);
            }
        }

        // Notify runner-up
        if (runnerUp != null && runnerUp.getUser() != null && runnerUp.getUser().getTelegramId() != null) {
            String runnerUpMessage = String.format(
                    "🥈 Tabriklaymiz! Siz finalga chiqdingiz!\n\n" +
                    "🏆 %s\n\n" +
                    "Finalda 2-o'rin - bu ham ajoyib natija! 🌟",
                    tournament.getName()
            );
            try {
                notificationService.notifyUser(runnerUp.getUser().getTelegramId(), runnerUpMessage);
            } catch (Exception e) {
                log.error("Failed to notify runner-up", e);
            }
        }
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
