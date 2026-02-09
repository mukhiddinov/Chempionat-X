package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.MatchResultService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.MatchResult;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.infrastructure.telegram.KeyboardFactory;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingResultsCommand implements TelegramCommand {

    private final MatchResultService matchResultService;
    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (!update.hasMessage()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();

        // Check if user is admin
        Optional<User> userOpt = userService.getUserByTelegramId(userId);
        if (userOpt.isEmpty() || (userOpt.get().getRole() != Role.ADMIN && userOpt.get().getRole() != Role.MODERATOR)) {
            bot.sendMessage(chatId, "❌ Faqat adminlar tasdiqlash uchun natijalarni ko'rishi mumkin.");
            return;
        }

        try {
            List<MatchResult> pendingResults = matchResultService.getPendingResults();

            if (pendingResults.isEmpty()) {
                bot.sendMessage(chatId, "✅ Tasdiqlash uchun natijalar yo'q.");
                return;
            }

            for (MatchResult result : pendingResults) {
                StringBuilder message = new StringBuilder();
                message.append("📋 Tasdiqlash uchun natija\n\n");
                message.append(String.format("🏠 %s: %d\n",
                        result.getMatch().getHomeTeam().getName(),
                        result.getHomeScore()));
                message.append(String.format("✈️ %s: %d\n\n",
                        result.getMatch().getAwayTeam().getName(),
                        result.getAwayScore()));
                message.append(String.format("👤 Yuboruvchi: %s\n",
                        result.getSubmittedBy().getUsername() != null ? 
                        "@" + result.getSubmittedBy().getUsername() : 
                        result.getSubmittedBy().getFirstName()));
                message.append(String.format("📅 Yuborilgan: %s\n",
                        result.getSubmittedAt().toString()));

                // Send screenshot if available
                if (result.getScreenshotUrl() != null && !result.getScreenshotUrl().isEmpty()) {
                    bot.sendPhoto(chatId, result.getScreenshotUrl(), message.toString());
                    
                    // Send approval keyboard in separate message
                    bot.sendMessage(chatId, "Tasdiqlaysizmi?", 
                                   KeyboardFactory.createResultApprovalKeyboard(result.getId()));
                } else {
                    bot.sendMessage(chatId, message.toString(), 
                                   KeyboardFactory.createResultApprovalKeyboard(result.getId()));
                }
            }

            log.info("Sent {} pending results to admin {}", pendingResults.size(), userId);

        } catch (Exception e) {
            log.error("Error showing pending results", e);
            bot.sendMessage(chatId, "Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    @Override
    public String getCommandName() {
        return "/pendingresults";
    }
}
