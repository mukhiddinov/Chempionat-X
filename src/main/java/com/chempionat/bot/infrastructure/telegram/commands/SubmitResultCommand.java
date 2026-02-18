package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.MatchResultService;
import com.chempionat.bot.application.service.MatchService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.TeamRepository;
import com.chempionat.bot.domain.repository.TournamentRepository;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import com.chempionat.bot.infrastructure.telegram.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmitResultCommand implements TelegramCommand {

    private final MatchService matchService;
    private final MatchResultService matchResultService;
    private final UserService userService;
    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final TournamentRepository tournamentRepository;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId;
        Long userId;

        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            userId = update.getCallbackQuery().getFrom().getId();
            String callbackData = update.getCallbackQuery().getData();
            
            // Handle different callback formats
            if (callbackData.startsWith("submit_result_user:")) {
                // Format: submit_result_user:userId:tournamentId
                handleShowHomeMatches(bot, chatId, userId, update.getCallbackQuery().getMessage().getMessageId(), callbackData);
            } else if (callbackData.startsWith("resubmit:")) {
                // Format: resubmit:matchId - handle resubmission after rejection
                handleResubmit(bot, chatId, userId, update.getCallbackQuery().getMessage().getMessageId(), callbackData);
            } else if (callbackData.startsWith("submitresult:")) {
                // Format: submitresult:matchId
                Long matchId = Long.parseLong(callbackData.split(":")[1]);
                
                // Check if user is home player
                Optional<Match> matchOpt = matchRepository.findById(matchId);
                if (matchOpt.isEmpty()) {
                    bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                            "❌ O'yin topilmadi.");
                    return;
                }
                
                Match match = matchOpt.get();
                if (!matchService.isHomePlayer(match, userId)) {
                    bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                            "❌ Faqat Home o'yinchi natijani yuborishi mumkin.");
                    return;
                }
                
                UserContext context = UserContext.get(userId);
                context.setCurrentCommand(getCommandName());
                context.setData("matchId", matchId);
                context.setData("step", "photo");
                
                bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                        "📸 O'yin natijasining screenshot rasmini yuboring:");
            }
            
        } else if (update.hasMessage()) {
            chatId = update.getMessage().getChatId();
            userId = update.getMessage().getFrom().getId();
            
            UserContext context = UserContext.get(userId);
            Long matchId = (Long) context.getData("matchId");
            String step = context.getDataAsString("step");
            
            if (matchId == null || step == null) {
                bot.sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos qaytadan boshlang.");
                context.clearData();
                return;
            }
            
            if (step.equals("photo") && update.getMessage().hasPhoto()) {
                handlePhotoSubmission(bot, chatId, userId, context, matchId, update);
            } else if (step.equals("score") && update.getMessage().hasText()) {
                handleScoreSubmission(bot, chatId, userId, context, matchId, update.getMessage().getText());
            } else if (step.equals("photo")) {
                bot.sendMessage(chatId, "❌ Iltimos rasm yuboring.");
            } else if (step.equals("score")) {
                bot.sendMessage(chatId, "❌ Iltimos natijani X:Y formatda yuboring (masalan: 3:2).");
            }
        }
    }
    
    private void handleResubmit(TelegramBot bot, Long chatId, Long userId, Integer messageId, String callbackData) {
        try {
            // Format: resubmit:matchId
            String[] parts = callbackData.split(":");
            if (parts.length < 2) {
                bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi.");
                return;
            }
            
            Long matchId = Long.parseLong(parts[1]);
            Optional<Match> matchOpt = matchRepository.findById(matchId);
            
            if (matchOpt.isEmpty()) {
                bot.editMessage(chatId, messageId, "❌ O'yin topilmadi.");
                return;
            }
            
            Match match = matchOpt.get();
            
            // Security: Only home team user can resubmit
            if (!matchService.isHomePlayer(match, userId)) {
                bot.editMessage(chatId, messageId, "❌ Faqat Home o'yinchi natijani qayta yuborishi mumkin.");
                return;
            }
            
            // Security: Only allow resubmit if status is REJECTED
            if (match.getState() != MatchLifecycleState.REJECTED) {
                if (match.getState() == MatchLifecycleState.APPROVED) {
                    bot.editMessage(chatId, messageId, "❌ Bu o'yin natijasi allaqachon tasdiqlangan. Qayta yuborish mumkin emas.");
                } else if (match.getState() == MatchLifecycleState.PENDING_APPROVAL) {
                    bot.editMessage(chatId, messageId, "❌ Bu o'yin natijasi hali ko'rib chiqilmoqda. Qayta yuborish mumkin emas.");
                } else {
                    bot.editMessage(chatId, messageId, "❌ Bu o'yin natijasini qayta yuborish mumkin emas.");
                }
                return;
            }
            
            // Show rejection reason if available
            String rejectReason = match.getRejectReason();
            String message = "📤 Natijani qayta yuborish\n\n";
            if (rejectReason != null && !rejectReason.isEmpty()) {
                message += "❌ Rad etilish sababi: " + rejectReason + "\n\n";
            }
            message += String.format("🏠 %s vs ✈️ %s\n\n",
                    match.getHomeTeam().getName(),
                    match.getAwayTeam().getName());
            message += "📸 O'yin natijasining screenshot rasmini yuboring:";
            
            // Start submission flow
            UserContext context = UserContext.get(userId);
            context.setCurrentCommand(getCommandName());
            context.setData("matchId", matchId);
            context.setData("step", "photo");
            context.setData("isResubmit", true);
            
            bot.editMessage(chatId, messageId, message);
            
            log.info("User {} started resubmission for match {}", userId, matchId);
            
        } catch (NumberFormatException e) {
            bot.editMessage(chatId, messageId, "❌ Noto'g'ri o'yin identifikatori.");
        } catch (Exception e) {
            log.error("Error handling resubmit callback", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }
    
    private void handleShowHomeMatches(TelegramBot bot, Long chatId, Long userId, Integer messageId, String callbackData) {
        try {
            String[] parts = callbackData.split(":");
            if (parts.length < 3) {
                bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
                return;
            }
            
            Long targetUserId = Long.parseLong(parts[1]); // This is database user ID, not Telegram ID
            Long tournamentId = Long.parseLong(parts[2]);
            
            Optional<User> userOpt = userService.getUserById(targetUserId); // Use getUserById instead
            if (userOpt.isEmpty()) {
                bot.editMessage(chatId, messageId, "❌ Foydalanuvchi topilmadi");
                return;
            }
            
            User user = userOpt.get();
            
            // Get user's home matches that are not played yet
            List<Match> homeMatches = matchRepository.findByHomeTeamOrAwayTeam(
                teamRepository.findByTournamentAndUser(
                    tournamentRepository.findById(tournamentId).orElse(null), 
                    user
                ).orElse(null), 
                null
            ).stream()
            .filter(m -> !Boolean.TRUE.equals(m.getIsBye()))
            .filter(m -> !m.getHomeTeam().getId().equals(m.getAwayTeam().getId()))
            .filter(m -> m.getHomeTeam().getUser().getId().equals(user.getId()))
            .filter(m -> m.getState() == MatchLifecycleState.CREATED || m.getState() == MatchLifecycleState.REJECTED)
            .sorted(Comparator.comparing(Match::getRound))
            .toList();
            
            if (homeMatches.isEmpty()) {
                bot.editMessage(chatId, messageId, 
                    "✅ Barcha o'yinlar o'ynalgan yoki sizga yuklash kerak bo'lgan o'yin yo'q");
                return;
            }
            
            // Build message with matches
            StringBuilder message = new StringBuilder();
            message.append("📤 Natija yuklash uchun o'yinlarni tanlang:\n\n");
            message.append("Faqat Home o'yinlaringiz ko'rsatilgan:\n\n");
            
            for (Match match : homeMatches) {
                String homeUsername = match.getHomeTeam().getUser().getUsername();
                String awayUsername = match.getAwayTeam().getUser().getUsername();
                
                // Handle null usernames
                if (homeUsername == null || homeUsername.isEmpty()) {
                    homeUsername = match.getHomeTeam().getUser().getFirstName();
                }
                if (awayUsername == null || awayUsername.isEmpty()) {
                    awayUsername = match.getAwayTeam().getUser().getFirstName();
                }
                
                message.append("⚽ Round ").append(match.getRound()).append("\n");
                message.append("   @").append(homeUsername)
                       .append(" vs @").append(awayUsername)
                       .append("\n\n");
            }
            
            // Create inline keyboard with match buttons
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new ArrayList<>();
            
            for (Match match : homeMatches) {
                String awayUsername = match.getAwayTeam().getUser().getUsername();
                if (awayUsername == null || awayUsername.isEmpty()) {
                    awayUsername = match.getAwayTeam().getUser().getFirstName();
                }
                
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton button = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
                button.setText("Round " + match.getRound() + ": vs @" + awayUsername);
                button.setCallbackData("submitresult:" + match.getId());
                row.add(button);
                rows.add(row);
            }
            
            keyboard.setKeyboard(rows);
            bot.editMessage(chatId, messageId, message.toString(), keyboard);
            
        } catch (Exception e) {
            log.error("Error showing home matches", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    private void handlePhotoSubmission(TelegramBot bot, Long chatId, Long userId, 
                                      UserContext context, Long matchId, Update update) {
        try {
            // Get the largest photo
            List<PhotoSize> photos = update.getMessage().getPhoto();
            PhotoSize largestPhoto = photos.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(photos.get(photos.size() - 1));
            
            String fileId = largestPhoto.getFileId();
            
            context.setData("photoFileId", fileId);
            context.setData("step", "score");
            
            bot.sendMessage(chatId, 
                    "✅ Rasm qabul qilindi!\n\n" +
                    "Endi natijani kiriting (X:Y formatda, masalan: 3:2):");
            
        } catch (Exception e) {
            log.error("Error handling photo submission", e);
            bot.sendMessage(chatId, "❌ Rasmni yuklashda xatolik yuz berdi. Qaytadan urinib ko'ring.");
            context.clearData();
        }
    }

    private void handleScoreSubmission(TelegramBot bot, Long chatId, Long userId, 
                                      UserContext context, Long matchId, String scoreText) {
        // Parse score (format: "X:Y") with regex validation
        if (!scoreText.trim().matches("^\\d{1,2}:\\d{1,2}$")) {
            bot.sendMessage(chatId, "❌ Noto'g'ri format. Iltimos X:Y formatda kiriting (masalan: 3:2).\n\nQaytadan urinib ko'ring:");
            return; // Keep session active, don't clear context
        }
        
        String[] parts = scoreText.trim().split(":");
        int homeScore;
        int awayScore;
        try {
            homeScore = Integer.parseInt(parts[0].trim());
            awayScore = Integer.parseInt(parts[1].trim());
            
            if (homeScore < 0 || awayScore < 0 || homeScore > 99 || awayScore > 99) {
                bot.sendMessage(chatId, "❌ Natija 0-99 orasida bo'lishi kerak.\n\nQaytadan urinib ko'ring:");
                return; // Keep session active
            }
        } catch (NumberFormatException e) {
            bot.sendMessage(chatId, "❌ Noto'g'ri raqam formati. Iltimos X:Y formatda kiriting.\n\nQaytadan urinib ko'ring:");
            return; // Keep session active
        }
        
        try {
            String photoFileId = context.getDataAsString("photoFileId");
            Optional<User> userOpt = userService.getUserByTelegramId(userId);
            Optional<Match> matchOpt = matchRepository.findById(matchId);
            
            if (userOpt.isEmpty() || matchOpt.isEmpty()) {
                bot.sendMessage(chatId, "❌ Foydalanuvchi yoki o'yin topilmadi.");
                context.clearData();
                return;
            }
            
            User user = userOpt.get();
            Match match = matchOpt.get();
            
            // Submit result
            matchResultService.submitResult(match, user, homeScore, awayScore, photoFileId);
            
            String message = String.format(
                    "✅ Natija muvaffaqiyatli yuborildi!\n\n" +
                    "🏠 %s: %d\n" +
                    "✈️ %s: %d\n\n" +
                    "Natija admin tomonidan tasdiqlanishi kutilmoqda.",
                    match.getHomeTeam().getName(),
                    homeScore,
                    match.getAwayTeam().getName(),
                    awayScore
            );
            
            bot.sendMessage(chatId, message);
            
            log.info("Result submitted for match {} by user {}: {}:{}", 
                     matchId, userId, homeScore, awayScore);
            
            // Clear context only on successful submission
            context.clearData();
            
        } catch (IllegalStateException e) {
            bot.sendMessage(chatId, "❌ " + e.getMessage());
            context.clearData();
        } catch (Exception e) {
            log.error("Error submitting result", e);
            bot.sendMessage(chatId, "❌ Natijani yuborishda xatolik yuz berdi. Qaytadan urinib ko'ring.");
            context.clearData();
        }
    }

    @Override
    public String getCommandName() {
        return "/submitresult";
    }
}
