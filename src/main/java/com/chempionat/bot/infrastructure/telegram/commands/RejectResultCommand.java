package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.MatchResultService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.MatchResult;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.MatchResultRepository;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import com.chempionat.bot.infrastructure.telegram.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RejectResultCommand implements TelegramCommand {

    private final MatchResultService matchResultService;
    private final MatchResultRepository matchResultRepository;
    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId;
        Long userId;

        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            userId = update.getCallbackQuery().getFrom().getId();
            String callbackData = update.getCallbackQuery().getData();

            Optional<User> userOpt = userService.getUserByTelegramId(userId);
            if (userOpt.isEmpty()) {
                bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                        "❌ Foydalanuvchi topilmadi.");
                return;
            }

            Long resultId = Long.parseLong(callbackData.split(":")[1]);

            MatchResult result = matchResultRepository.findById(resultId)
                    .orElseThrow(() -> new IllegalArgumentException("Result not found"));

            User reviewer = userOpt.get();
            boolean isAdminOrModerator = reviewer.getRole() == Role.ADMIN || reviewer.getRole() == Role.MODERATOR;
            boolean isTournamentOwner = result.getMatch().getTournament().getCreatedBy().getId().equals(reviewer.getId());

            if (!isAdminOrModerator && !isTournamentOwner) {
                bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                        "❌ Faqat turnir tashkilotchisi yoki admin natijani rad etishi mumkin.");
                return;
            }

            
            UserContext context = UserContext.get(userId);
            context.setCurrentCommand(getCommandName());
            context.setData("resultId", resultId);
            
            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    "Rad etish sababini kiriting:");
            
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            userId = update.getMessage().getFrom().getId();
            
            UserContext context = UserContext.get(userId);
            Long resultId = (Long) context.getData("resultId");
            String comment = update.getMessage().getText();
            
            if (resultId == null) {
                bot.sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos qaytadan boshlang.");
                context.clearData();
                return;
            }
            
            try {
                Optional<User> userOpt = userService.getUserByTelegramId(userId);
                if (userOpt.isEmpty()) {
                    bot.sendMessage(chatId, "❌ Foydalanuvchi topilmadi.");
                    context.clearData();
                    return;
                }
                
                User reviewer = userOpt.get();
                
                matchResultService.rejectResult(resultId, reviewer, comment);
                
                String message = String.format(
                        "❌ Natija rad etildi!\n\n" +
                        "Sabab: %s\n\n" +
                        "O'yinchi qayta yuborishi mumkin.",
                        comment
                );
                
                bot.sendMessage(chatId, message);
                
                log.info("Result {} rejected by user {} with comment: {}", resultId, userId, comment);
                
            } catch (IllegalArgumentException e) {
                bot.sendMessage(chatId, "❌ Natija topilmadi.");
            } catch (Exception e) {
                log.error("Error rejecting result", e);
                bot.sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
            } finally {
                context.clearData();
            }
        }
    }

    @Override
    public String getCommandName() {
        return "/reject";
    }
}
