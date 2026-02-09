package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.MatchResult;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.MatchResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchResultService {

    private final MatchResultRepository matchResultRepository;
    private final MatchRepository matchRepository;
    private final NotificationService notificationService;

    @Transactional
    public MatchResult submitResult(Match match, User submittedBy, Integer homeScore, 
                                    Integer awayScore, String screenshotUrl) {
        
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
                .build();
        
        // Update match state to PENDING_APPROVAL
        match.setState(MatchLifecycleState.PENDING_APPROVAL);
        matchRepository.save(match);
        
        MatchResult saved = matchResultRepository.save(result);
        log.info("Result submitted for match {}: {}:{}", match.getId(), homeScore, awayScore);
        
        // AUTOMATIC NOTIFICATION TO TOURNAMENT ORGANIZER
        notifyOrganizerAboutResult(saved);
        
        return saved;
    }

    @Transactional
    public void approveResult(Long resultId, User reviewer) {
        MatchResult result = matchResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Result not found"));
        
        if (result.getIsApproved()) {
            throw new IllegalStateException("Result already approved");
        }
        
        result.setIsApproved(true);
        result.setReviewedBy(reviewer);
        result.setReviewedAt(LocalDateTime.now());
        
        // Update match with final scores
        Match match = result.getMatch();
        match.setHomeScore(result.getHomeScore());
        match.setAwayScore(result.getAwayScore());
        match.setState(MatchLifecycleState.APPROVED);
        
        matchRepository.save(match);
        matchResultRepository.save(result);
        
        log.info("Result approved for match {}: {}:{}", match.getId(), 
                result.getHomeScore(), result.getAwayScore());
                
        // Notify submitter about approval
        notificationService.notifyUser(result.getSubmittedBy().getTelegramId(),
                String.format("✅ Sizning natijangiz tasdiqlandi!\n\n" +
                        "🏠 %s: %d\n" +
                        "✈️ %s: %d",
                        match.getHomeTeam().getName(),
                        result.getHomeScore(),
                        match.getAwayTeam().getName(),
                        result.getAwayScore()));
    }

    @Transactional
    public void rejectResult(Long resultId, User reviewer, String comment) {
        MatchResult result = matchResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Result not found"));
        
        // Get match and submitter before modifying associations
        Match match = result.getMatch();
        Long submitterTelegramId = result.getSubmittedBy().getTelegramId();
        
        // Break bidirectional association to avoid cascading a deleted entity
        match.setResult(null);
        result.setMatch(null);
        matchRepository.save(match);
        
        // Delete the rejected result so user can resubmit
        matchResultRepository.delete(result);
        
        // Reset match state to CREATED so it can be played again
        match.setState(MatchLifecycleState.CREATED);
        match.setHomeScore(null);
        match.setAwayScore(null);
        matchRepository.save(match);
        
        log.info("Result rejected and deleted for match {}. User can resubmit.", match.getId());
        
        // Notify submitter about rejection
        notificationService.notifyUser(submitterTelegramId,
                String.format("❌ Sizning natijangiz rad etildi.\n\n" +
                        "Sabab: %s\n\n" +
                        "Iltimos qaytadan yuboring.",
                        comment));
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
        message.append(String.format("✈️ %s (%s): %d\n\n",
                match.getAwayTeam().getName(),
                match.getAwayTeam().getUser().getUsername() != null ? 
                "@" + match.getAwayTeam().getUser().getUsername() : 
                match.getAwayTeam().getUser().getFirstName(),
                result.getAwayScore()));
        message.append(String.format("👤 Yuboruvchi: %s\n",
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
        
        log.info("Notification sent to organizer {} about match result: {}", 
                 organizer.getTelegramId(), result.getId());
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
