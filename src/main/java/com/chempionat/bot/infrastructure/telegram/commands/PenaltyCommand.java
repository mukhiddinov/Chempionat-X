package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.MatchResultService;
import com.chempionat.bot.application.service.SingleEliminationService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.MatchResult;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.MatchResultRepository;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Command to submit penalty shootout results for drawn knockout matches.
 * 
 * USER-DRIVEN PENALTY: The user who submitted the result provides penalty.
 * 
 * Usage: /penalty <matchId> <homePenalties> <awayPenalties>
 * Example: /penalty 42 5 4
 * 
 * Allowed users:
 * - User who submitted the original result (home team player)
 * - Tournament organizer (fallback)
 * - Admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PenaltyCommand implements TelegramCommand {

    private final UserService userService;
    private final MatchRepository matchRepository;
    private final MatchResultRepository matchResultRepository;
    private final SingleEliminationService singleEliminationService;
    private final MatchResultService matchResultService;

    @Override
    @Transactional
    public void execute(Update update, TelegramBot bot) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();

        // Get user
        Optional<User> userOpt = userService.getUserByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            bot.sendMessage(chatId, "❌ Foydalanuvchi topilmadi. Avval /start buyrug'ini yuboring.");
            return;
        }

        User user = userOpt.get();

        // Parse command: /penalty <matchId> <homePenalties> <awayPenalties>
        String[] parts = messageText.split("\\s+");
        if (parts.length != 4) {
            bot.sendMessage(chatId, 
                    "❌ Noto'g'ri format.\n\n" +
                    "To'g'ri format: /penalty <matchId> <uy_penalti> <mehmon_penalti>\n" +
                    "Masalan: /penalty 42 5 4");
            return;
        }

        Long matchId;
        Integer homePenalty;
        Integer awayPenalty;

        try {
            matchId = Long.parseLong(parts[1]);
            homePenalty = Integer.parseInt(parts[2]);
            awayPenalty = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            bot.sendMessage(chatId, "❌ Noto'g'ri raqam formati. Match ID va penalti natijalar son bo'lishi kerak.");
            return;
        }

        // Validate penalty scores
        if (homePenalty < 0 || awayPenalty < 0) {
            bot.sendMessage(chatId, "❌ Penalti natijasi manfiy bo'lishi mumkin emas.");
            return;
        }

        if (homePenalty.equals(awayPenalty)) {
            bot.sendMessage(chatId, "❌ Penalti seriyasi durrang bilan tugashi mumkin emas. Natijalar turlicha bo'lishi kerak.");
            return;
        }

        // Find match
        Optional<Match> matchOpt = matchRepository.findById(matchId);
        if (matchOpt.isEmpty()) {
            bot.sendMessage(chatId, "❌ Match topilmadi: ID=" + matchId);
            return;
        }

        Match match = matchOpt.get();

        // Check match state
        if (match.getState() != MatchLifecycleState.PENDING_PENALTY) {
            bot.sendMessage(chatId, 
                    String.format("❌ Bu match penalti kutmoqda emas.\n" +
                            "Joriy holat: %s\n\n" +
                            "Penalti faqat PENDING_PENALTY holatidagi matchlar uchun kiritiladi.",
                            match.getState()));
            return;
        }

        // Get the match result
        Optional<MatchResult> resultOpt = matchResultRepository.findByMatch(match);
        if (resultOpt.isEmpty()) {
            bot.sendMessage(chatId, "❌ Match natijasi topilmadi.");
            return;
        }
        
        MatchResult result = resultOpt.get();

        // USER-DRIVEN PENALTY: Check if user is allowed to submit penalty
        // Allowed: user who submitted result, tournament organizer, or admin
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOrganizer = match.getTournament().getCreatedBy().getId().equals(user.getId());
        boolean isSubmitter = result.getSubmittedBy().getId().equals(user.getId());
        
        if (!isAdmin && !isOrganizer && !isSubmitter) {
            bot.sendMessage(chatId, "❌ Faqat natija yuborgan o'yinchi, tashkilotchi yoki admin penalti kiritishi mumkin.");
            return;
        }

        // Update match with penalty scores
        match.setHomePenaltyScore(homePenalty);
        match.setAwayPenaltyScore(awayPenalty);
        match.setDecidedByPenalties(true);
        match.setState(MatchLifecycleState.APPROVED);
        matchRepository.save(match);

        // Update result with penalty scores
        result.setHomePenaltyScore(homePenalty);
        result.setAwayPenaltyScore(awayPenalty);
        matchResultRepository.save(result);

        // Determine winner
        Team winner = singleEliminationService.determineWinner(match);
        Team loser = singleEliminationService.determineLoser(match);

        log.info("PENALTY_SUBMITTED: matchId={}, tournamentId={}, penalties={}:{}, winner={}, submittedBy={}", 
                matchId, match.getTournament().getId(), homePenalty, awayPenalty, 
                winner != null ? winner.getName() : "none", user.getTelegramId());

        // Update tournament timestamp
        match.getTournament().setUpdatedAt(LocalDateTime.now());

        // Send confirmation
        String winnerName = winner != null ? winner.getName() : "Noma'lum";
        bot.sendMessage(chatId, 
                String.format("✅ Penalti natijasi saqlandi!\n\n" +
                        "🏆 Match #%d\n" +
                        "⚽ Asosiy vaqt: %d:%d\n" +
                        "🎯 Penaltilar: %d:%d\n\n" +
                        "🏅 G'olib: %s\n\n" +
                        "G'olib keyingi bosqichga o'tkazildi.",
                        matchId,
                        match.getHomeScore(), match.getAwayScore(),
                        homePenalty, awayPenalty,
                        winnerName));

        // Propagate winner to next match
        singleEliminationService.propagateWinner(match);

        // Check for third-place match creation if this was a semifinal
        if (match.getStage() == com.chempionat.bot.domain.enums.MatchStage.SEMI_FINAL) {
            singleEliminationService.checkAndCreateThirdPlaceMatch(match);
        }

        // Notify teams
        if (winner != null && winner.getUser() != null) {
            try {
                bot.sendMessage(winner.getUser().getTelegramId(),
                        String.format("🎉 Tabriklaymiz! Penalti seriyasida g'olib chiqdingiz!\n\n" +
                                "⚽ Natija: %d:%d (pen: %d:%d)\n" +
                                "Siz keyingi bosqichga o'tdingiz!",
                                match.getHomeScore(), match.getAwayScore(),
                                homePenalty, awayPenalty));
            } catch (Exception e) {
                log.warn("Failed to notify winner team", e);
            }
        }

        if (loser != null && loser.getUser() != null) {
            try {
                // Check if this is semifinal - loser plays 3rd place match
                boolean isSemifinal = match.getStage() == com.chempionat.bot.domain.enums.MatchStage.SEMI_FINAL;
                String loserMessage = isSemifinal 
                        ? String.format("😔 Penalti seriyasida mag'lub bo'ldingiz.\n\n" +
                                "⚽ Natija: %d:%d (pen: %d:%d)\n\n" +
                                "🥉 Lekin hali tugamadi! 3-o'rin uchun o'yin kutmoqda.",
                                match.getHomeScore(), match.getAwayScore(),
                                homePenalty, awayPenalty)
                        : String.format("😔 Penalti seriyasida mag'lub bo'ldingiz.\n\n" +
                                "⚽ Natija: %d:%d (pen: %d:%d)\n\n" +
                                "Turnirda ishtirok etganingiz uchun rahmat!",
                                match.getHomeScore(), match.getAwayScore(),
                                homePenalty, awayPenalty);
                
                bot.sendMessage(loser.getUser().getTelegramId(), loserMessage);
            } catch (Exception e) {
                log.warn("Failed to notify loser team", e);
            }
        }
    }

    @Override
    public String getCommandName() {
        return "/penalty";
    }
}
