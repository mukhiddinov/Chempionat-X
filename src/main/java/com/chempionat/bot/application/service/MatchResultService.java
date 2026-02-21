package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.enums.MatchStage;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.MatchResult;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.MatchResultRepository;
import com.chempionat.bot.domain.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MatchResultService {

    private final MatchResultRepository matchResultRepository;
    private final MatchRepository matchRepository;
    private final TournamentRepository tournamentRepository;
    private final NotificationService notificationService;
    private final TournamentCompletionService tournamentCompletionService;
    private final SingleEliminationService singleEliminationService;

    public MatchResultService(
            MatchResultRepository matchResultRepository,
            MatchRepository matchRepository,
            TournamentRepository tournamentRepository,
            NotificationService notificationService,
            @Lazy TournamentCompletionService tournamentCompletionService,
            @Lazy SingleEliminationService singleEliminationService) {
        this.matchResultRepository = matchResultRepository;
        this.matchRepository = matchRepository;
        this.tournamentRepository = tournamentRepository;
        this.notificationService = notificationService;
        this.tournamentCompletionService = tournamentCompletionService;
        this.singleEliminationService = singleEliminationService;
    }

    @Transactional
    public MatchResult submitResult(Match match, User submittedBy, Integer homeScore, 
                                    Integer awayScore, String screenshotUrl) {
        return submitResultWithPenalty(match, submittedBy, homeScore, awayScore, screenshotUrl, null, null);
    }
    
    /**
     * Submit match result with optional penalty scores.
     * For knockout draws, the user provides penalty scores at submission time.
     * This is user-driven: penalty is collected from user, not organizer.
     */
    @Transactional
    public MatchResult submitResultWithPenalty(Match match, User submittedBy, Integer homeScore, 
                                               Integer awayScore, String screenshotUrl,
                                               Integer homePenalty, Integer awayPenalty) {
        
        // Validate that submitter is the home team player
        if (!match.getHomeTeam().getUser().getId().equals(submittedBy.getId())) {
            throw new IllegalArgumentException("Only home player can submit result");
        }
        
        // Check if result already submitted
        if (matchResultRepository.findByMatch(match).isPresent()) {
            throw new IllegalStateException("Result already submitted for this match");
        }
        
        MatchResult result = MatchResult.builder()
                .match(match)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .screenshotUrl(screenshotUrl)
                .submittedBy(submittedBy)
                .isApproved(false)
                .homePenaltyScore(homePenalty)
                .awayPenaltyScore(awayPenalty)
                .build();
        
        // Update match state to PENDING_APPROVAL
        match.setState(MatchLifecycleState.PENDING_APPROVAL);
        matchRepository.save(match);
        
        MatchResult saved = matchResultRepository.save(result);
        
        // Log with penalty info if present
        if (homePenalty != null && awayPenalty != null) {
            log.info("Result submitted for match {}: {}:{} (pen: {}:{})", 
                    match.getId(), homeScore, awayScore, homePenalty, awayPenalty);
        } else {
            log.info("Result submitted for match {}: {}:{}", match.getId(), homeScore, awayScore);
        }
        
        // AUTOMATIC NOTIFICATION TO TOURNAMENT ORGANIZER (includes penalty info if present)
        notifyOrganizerAboutResult(saved);
        
        return saved;
    }

    @Transactional
    public void approveResult(Long resultId, User reviewer) {
        MatchResult result = matchResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Result not found"));
        
        Match match = result.getMatch();
        Long matchId = match.getId();
        Long tournamentId = match.getTournament().getId();
        Integer round = match.getRound();
        MatchLifecycleState previousState = match.getState();
        
        // IDEMPOTENCY CHECK: If already approved, return gracefully (no exception)
        if (result.getIsApproved()) {
            log.warn("APPROVAL_IDEMPOTENT: resultId={}, matchId={}, tournamentId={} - already approved, ignoring duplicate request", 
                    resultId, matchId, tournamentId);
            return;
        }
        
        // STATE VALIDATION: Verify match is still in PENDING_APPROVAL state
        // This protects against race conditions where state changed after initial load
        if (previousState != MatchLifecycleState.PENDING_APPROVAL) {
            log.warn("APPROVAL_REJECTED: resultId={}, matchId={}, tournamentId={}, currentState={} - invalid state for approval", 
                    resultId, matchId, tournamentId, previousState);
            throw new IllegalStateException("Match is not in PENDING_APPROVAL state (current: " + previousState + ")");
        }
        
        // PESSIMISTIC LOCK: Acquire row-level lock to prevent concurrent modifications
        Match lockedMatch = matchRepository.findByIdWithLock(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
        
        // RE-VALIDATE state after acquiring lock (double-check pattern)
        if (lockedMatch.getState() != MatchLifecycleState.PENDING_APPROVAL) {
            log.warn("APPROVAL_RACE_DETECTED: resultId={}, matchId={}, tournamentId={}, lockedState={} - state changed during lock acquisition", 
                    resultId, matchId, tournamentId, lockedMatch.getState());
            throw new IllegalStateException("Match state changed during approval (now: " + lockedMatch.getState() + ")");
        }
        
        // Perform approval
        result.setIsApproved(true);
        result.setReviewedBy(reviewer);
        result.setReviewedAt(LocalDateTime.now());
        
        // Update match with final scores
        lockedMatch.setHomeScore(result.getHomeScore());
        lockedMatch.setAwayScore(result.getAwayScore());
        
        // Copy penalty scores if present
        if (result.getHomePenaltyScore() != null && result.getAwayPenaltyScore() != null) {
            lockedMatch.setHomePenaltyScore(result.getHomePenaltyScore());
            lockedMatch.setAwayPenaltyScore(result.getAwayPenaltyScore());
            lockedMatch.setDecidedByPenalties(true);
        }
        
        Tournament tournament = lockedMatch.getTournament();
        
        // CHECK FOR DRAW IN KNOCKOUT: Requires penalty shootout
        boolean isDraw = result.getHomeScore().equals(result.getAwayScore());
        boolean isKnockout = tournament.getType() == TournamentType.PLAYOFF;
        boolean hasPenalties = result.getHomePenaltyScore() != null && result.getAwayPenaltyScore() != null;
        
        if (isDraw && isKnockout && !hasPenalties) {
            // Draw in knockout without penalties - set state to PENDING_PENALTY
            lockedMatch.setState(MatchLifecycleState.PENDING_PENALTY);
            matchRepository.save(lockedMatch);
            matchResultRepository.save(result);
            
            log.info("APPROVAL_DRAW_PENALTY_REQUIRED: resultId={}, matchId={}, tournamentId={}, score={}:{}", 
                    resultId, matchId, tournamentId, result.getHomeScore(), result.getAwayScore());
            
            // Notify organizer that penalty shootout result is needed
            notifyPenaltyRequired(lockedMatch, result);
            return;
        }
        
        // Validate penalty winner (if penalties, they shouldn't also be a draw)
        if (hasPenalties && result.getHomePenaltyScore().equals(result.getAwayPenaltyScore())) {
            log.error("PENALTY_DRAW_INVALID: resultId={}, matchId={} - penalty scores also tied {}:{}", 
                    resultId, matchId, result.getHomePenaltyScore(), result.getAwayPenaltyScore());
            throw new IllegalArgumentException("Penalty series cannot end in a draw");
        }
        
        lockedMatch.setState(MatchLifecycleState.APPROVED);
        
        matchRepository.save(lockedMatch);
        matchResultRepository.save(result);
        
        // Update tournament.updatedAt to invalidate standings image cache
        tournament.setUpdatedAt(LocalDateTime.now());
        tournamentRepository.save(tournament);
        
        // Build result string with penalty info if applicable
        String scoreString = hasPenalties 
                ? String.format("%d:%d (pen: %d:%d)", result.getHomeScore(), result.getAwayScore(), 
                        result.getHomePenaltyScore(), result.getAwayPenaltyScore())
                : String.format("%d:%d", result.getHomeScore(), result.getAwayScore());
        
        log.info("APPROVAL_SUCCESS: resultId={}, matchId={}, tournamentId={}, round={}, previousState={}, newState=APPROVED, score={}, decidedByPenalties={}", 
                resultId, matchId, tournamentId, round, previousState, scoreString, hasPenalties);
                
        // Notify submitter about approval
        String notificationMessage = hasPenalties 
                ? String.format("✅ Sizning natijangiz tasdiqlandi!\n\n" +
                        "🏠 %s: %d\n" +
                        "✈️ %s: %d\n\n" +
                        "⚽ Penaltilar: %d:%d",
                        lockedMatch.getHomeTeam().getName(), result.getHomeScore(),
                        lockedMatch.getAwayTeam().getName(), result.getAwayScore(),
                        result.getHomePenaltyScore(), result.getAwayPenaltyScore())
                : String.format("✅ Sizning natijangiz tasdiqlandi!\n\n" +
                        "🏠 %s: %d\n" +
                        "✈️ %s: %d",
                        lockedMatch.getHomeTeam().getName(), result.getHomeScore(),
                        lockedMatch.getAwayTeam().getName(), result.getAwayScore());
        
        notificationService.notifyUser(result.getSubmittedBy().getTelegramId(), notificationMessage);
        
        // Handle playoff-specific logic: winner propagation
        if (isKnockout) {
            handlePlayoffMatchApproval(lockedMatch);
        } else {
            // For league tournaments, check if tournament is complete
            tournamentCompletionService.checkAndNotifyIfComplete(tournament);
        }
    }
    
    /**
     * Notify USER who submitted result that penalty shootout result is required.
     * This is for edge cases where a draw was submitted without penalty.
     * 
     * USER-DRIVEN PENALTY: The submitting user provides penalty, not organizer.
     */
    private void notifyPenaltyRequired(Match match, MatchResult result) {
        Tournament tournament = match.getTournament();
        User submitter = result.getSubmittedBy();
        
        String message = String.format(
                "⚠️ Durrang! Penalti seriyasi kerak!\n\n" +
                "🏆 Turnir: %s\n" +
                "🆔 Match ID: %d\n\n" +
                "🏠 %s: %d\n" +
                "✈️ %s: %d\n\n" +
                "Bu playoff o'yini durrang bilan tugadi.\n" +
                "Penalti natijasini kiriting:\n" +
                "/penalty %d <uy_penalti> <mehmon_penalti>\n\n" +
                "Masalan: /penalty %d 5 4\n\n" +
                "❗ Penalti durrang bo'lishi mumkin emas.",
                tournament.getName(),
                match.getId(),
                match.getHomeTeam().getName(),
                result.getHomeScore(),
                match.getAwayTeam().getName(),
                result.getAwayScore(),
                match.getId(),
                match.getId()
        );
        
        // Notify the USER who submitted, not organizer
        notificationService.notifyUser(submitter.getTelegramId(), message);
        
        log.info("PENALTY_REQUESTED: matchId={}, userId={} - user-driven penalty request sent", 
                match.getId(), submitter.getTelegramId());
    }
    
    /**
     * Handle playoff match approval: propagate winner, create third-place match if semifinal,
     * and notify eliminated team.
     */
    private void handlePlayoffMatchApproval(Match match) {
        // Handle third-place match specially - no propagation, just notifications
        if (match.getStage() == MatchStage.THIRD_PLACE || Boolean.TRUE.equals(match.getIsThirdPlaceMatch())) {
            handleThirdPlaceMatchCompletion(match);
            return;
        }
        
        // Propagate winner to next match
        singleEliminationService.propagateWinner(match);
        
        // If this is a semifinal, check if we need to create third-place match
        if (match.getStage() == MatchStage.SEMI_FINAL) {
            singleEliminationService.checkAndCreateThirdPlaceMatch(match);
        }
        
        // Notify eliminated team
        Team loser = singleEliminationService.determineLoser(match);
        if (loser != null && !Boolean.TRUE.equals(match.getIsBye())) {
            // For semifinals: check third-place match status
            if (match.getStage() == MatchStage.SEMI_FINAL) {
                // Check if third-place match was created (4+ player tournament)
                boolean thirdPlaceExists = singleEliminationService.hasThirdPlaceMatch(match.getTournament());
                if (thirdPlaceExists) {
                    // Third-place match exists - loser will play there, no elimination notification
                    log.debug("SEMIFINAL_LOSER: {} will play third-place match", loser.getName());
                } else {
                    // No third-place match (3-player tournament)
                    // Loser is auto-assigned 3rd place via checkAndCreateThirdPlaceMatch()
                    // That method sends 🥉 notification, so we don't send elimination here
                    log.debug("SEMIFINAL_LOSER: {} auto-assigned 3rd place (3-player tournament)", loser.getName());
                }
            } else {
                // Non-semifinal losers are eliminated
                singleEliminationService.notifyElimination(loser, match);
            }
        }
    }
    
    /**
     * Handle third-place match completion - send 🥉 to winner and 4th place to loser.
     * Also checks if tournament is now fully complete.
     */
    private void handleThirdPlaceMatchCompletion(Match match) {
        Tournament tournament = match.getTournament();
        Team winner = singleEliminationService.determineWinner(match);
        Team loser = singleEliminationService.determineLoser(match);
        
        log.info("THIRD_PLACE_COMPLETE: tournamentId={}, matchId={}, winner={}, loser={}", 
                tournament.getId(), match.getId(), 
                winner != null ? winner.getName() : "null",
                loser != null ? loser.getName() : "null");
        
        // Build score string (include penalties if applicable)
        String scoreStr = buildScoreString(match);
        
        // Notify 🥉 winner
        if (winner != null && winner.getUser() != null && winner.getUser().getTelegramId() != null) {
            String winnerMessage = String.format(
                    "🥉 Tabriklaymiz! Siz 3-o'rinni egallangiz!\n\n" +
                    "🏆 %s\n\n" +
                    "3-o'rin uchun o'yin:\n%s\n\n" +
                    "Bronza medal sizniki! 🎉",
                    tournament.getName(), scoreStr
            );
            notificationService.notifyUser(winner.getUser().getTelegramId(), winnerMessage);
        }
        
        // Notify 4th place loser
        if (loser != null && loser.getUser() != null && loser.getUser().getTelegramId() != null) {
            String loserMessage = String.format(
                    "📊 Siz 4-o'rinni egallangiz.\n\n" +
                    "🏆 %s\n\n" +
                    "3-o'rin uchun o'yin:\n%s\n\n" +
                    "Keyingi turnirda omad! 🍀",
                    tournament.getName(), scoreStr
            );
            notificationService.notifyUser(loser.getUser().getTelegramId(), loserMessage);
        }
        
        // Check if tournament is now complete (final + third-place both done)
        singleEliminationService.checkTournamentCompletion(tournament);
    }
    
    /**
     * Build score string for notifications, including penalties if applicable.
     */
    private String buildScoreString(Match match) {
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

    @Transactional
    public void rejectResult(Long resultId, User reviewer, String comment) {
        MatchResult result = matchResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Result not found"));
        
        // Get match and submitter before modifying associations
        Match match = result.getMatch();
        Long submitterTelegramId = result.getSubmittedBy().getTelegramId();
        Long matchId = match.getId();
        
        // Break bidirectional association to avoid cascading a deleted entity
        match.setResult(null);
        result.setMatch(null);
        matchRepository.save(match);
        
        // Delete the rejected result so user can resubmit
        matchResultRepository.delete(result);
        
        // Set match state to REJECTED and store the reason
        match.setState(MatchLifecycleState.REJECTED);
        match.setRejectReason(comment);
        match.setHomeScore(null);
        match.setAwayScore(null);
        matchRepository.save(match);
        
        log.info("Result rejected for match {}. Reason: {}. User can resubmit.", matchId, comment);
        
        // Notify submitter about rejection with resubmit button
        String message = String.format(
                "❌ Sizning natijangiz rad etildi.\n\n" +
                "Sabab: %s\n\n" +
                "Qayta yuborish uchun quyidagi tugmani bosing:",
                comment);
        
        InlineKeyboardMarkup keyboard = createResubmitKeyboard(matchId);
        notificationService.notifyUserWithKeyboard(submitterTelegramId, message, keyboard);
    }
    
    private InlineKeyboardMarkup createResubmitKeyboard(Long matchId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton resubmitButton = InlineKeyboardButton.builder()
                .text("🔁 Qayta natija yuborish")
                .callbackData("resubmit:" + matchId)
                .build();
        row.add(resubmitButton);
        rows.add(row);
        
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    public List<MatchResult> getPendingResults() {
        return matchResultRepository.findByIsApprovedFalseAndReviewedByIsNull();
    }

    public List<MatchResult> getApprovedResults() {
        return matchResultRepository.findByIsApprovedTrue();
    }
    
    /**
     * Send automatic notification to tournament organizer about new result
     */
    private void notifyOrganizerAboutResult(MatchResult result) {
        Match match = result.getMatch();
        User organizer = match.getTournament().getCreatedBy();
        
        StringBuilder message = new StringBuilder();
        message.append("🔔 Yangi natija!\n\n");
        message.append(String.format("🏆 Turnir: %s\n", match.getTournament().getName()));
        message.append(String.format("🆔 Match ID: %d\n\n", match.getId()));
        message.append(String.format("🏠 %s (%s): %d\n",
                match.getHomeTeam().getName(),
                match.getHomeTeam().getUser().getUsername() != null ? 
                "@" + match.getHomeTeam().getUser().getUsername() : 
                match.getHomeTeam().getUser().getFirstName(),
                result.getHomeScore()));
        message.append(String.format("✈️ %s (%s): %d\n",
                match.getAwayTeam().getName(),
                match.getAwayTeam().getUser().getUsername() != null ? 
                "@" + match.getAwayTeam().getUser().getUsername() : 
                match.getAwayTeam().getUser().getFirstName(),
                result.getAwayScore()));
        
        // Include penalty info if provided by user (user-driven penalty)
        boolean hasPenalty = result.getHomePenaltyScore() != null && result.getAwayPenaltyScore() != null;
        if (hasPenalty) {
            message.append(String.format("\n🎯 Penaltilar: %d:%d\n", 
                    result.getHomePenaltyScore(), result.getAwayPenaltyScore()));
            message.append("(Durrang - penalti bilan hal bo'ldi)\n");
        }
        
        message.append(String.format("\n👤 Yuboruvchi: %s\n",
                result.getSubmittedBy().getUsername() != null ? 
                "@" + result.getSubmittedBy().getUsername() : 
                result.getSubmittedBy().getFirstName()));
        
        // Create inline keyboard
        InlineKeyboardMarkup keyboard = createResultApprovalKeyboard(result.getId());
        
        // Send screenshot with caption
        if (result.getScreenshotUrl() != null && !result.getScreenshotUrl().isEmpty()) {
            notificationService.notifyUserWithPhoto(
                    organizer.getTelegramId(),
                    result.getScreenshotUrl(),
                    message.toString(),
                    keyboard
            );
        } else {
            notificationService.notifyUserWithKeyboard(
                    organizer.getTelegramId(),
                    message.toString(),
                    keyboard
            );
        }
        
        log.info("Notification sent to organizer {} about match result: {} (penalty: {})", 
                 organizer.getTelegramId(), result.getId(), hasPenalty);
    }
    
    private InlineKeyboardMarkup createResultApprovalKeyboard(Long resultId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton approveButton = InlineKeyboardButton.builder()
                .text("✅ Tasdiqlash")
                .callbackData("approve:" + resultId)
                .build();
        
        InlineKeyboardButton rejectButton = InlineKeyboardButton.builder()
                .text("❌ Rad etish")
                .callbackData("reject:" + resultId)
                .build();

        row.add(approveButton);
        row.add(rejectButton);
        rows.add(row);

        keyboard.setKeyboard(rows);
        return keyboard;
    }
}
