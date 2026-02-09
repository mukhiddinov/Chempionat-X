package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.MatchResultService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.MatchResult;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.MatchResultRepository;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApproveResultCommand implements TelegramCommand {

    private final MatchResultService matchResultService;
    private final MatchResultRepository matchResultRepository;
    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (!update.hasCallbackQuery()) {
            return;
        }

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        String callbackData = update.getCallbackQuery().getData();

        Optional<User> userOpt = userService.getUserByTelegramId(userId);
        if (userOpt.isEmpty()) {
            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    "❌ Foydalanuvchi topilmadi.");
            return;
        }

        try {
            Long resultId = Long.parseLong(callbackData.split(":")[1]);
            User reviewer = userOpt.get();

            MatchResult result = matchResultRepository.findById(resultId)
                    .orElseThrow(() -> new IllegalArgumentException("Result not found"));

            boolean isAdminOrModerator = reviewer.getRole() == Role.ADMIN || reviewer.getRole() == Role.MODERATOR;
            boolean isTournamentOwner = result.getMatch().getTournament().getCreatedBy().getId().equals(reviewer.getId());

            if (!isAdminOrModerator && !isTournamentOwner) {
                bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                        "❌ Faqat turnir tashkilotchisi yoki admin natijani tasdiqlashi mumkin.");
                return;
            }

            matchResultService.approveResult(resultId, reviewer);

            String message = "✅ Natija tasdiqlandi!\n\n" +
                           "Tasdiqlagan: " + (reviewer.getUsername() != null ? 
                           "@" + reviewer.getUsername() : reviewer.getFirstName());

            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(), message);

            log.info("Result {} approved by user {}", resultId, userId);

        } catch (IllegalArgumentException e) {
            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    "❌ Natija topilmadi.");
        } catch (IllegalStateException e) {
            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    "❌ " + e.getMessage());
        } catch (Exception e) {
            log.error("Error approving result", e);
            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    "❌ Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    @Override
    public String getCommandName() {
        return "/approve";
    }
}
