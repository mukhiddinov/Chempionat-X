package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.OrganizerRequestService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.User;
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
public class RejectOrganizerCommand implements TelegramCommand {

    private final OrganizerRequestService requestService;
    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId;
        Long userId;

        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            userId = update.getCallbackQuery().getFrom().getId();
            String callbackData = update.getCallbackQuery().getData();

            // Check if user is admin
            Optional<User> userOpt = userService.getUserByTelegramId(userId);
            if (userOpt.isEmpty() || userOpt.get().getRole() != Role.ADMIN) {
                bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                        "❌ Faqat adminlar tashkilotchi so'rovini rad etishi mumkin.");
                return;
            }

            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            
            UserContext context = UserContext.get(userId);
            context.setCurrentCommand(getCommandName());
            context.setData("requestId", requestId);
            
            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    "Rad etish sababini kiriting:");
            
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            userId = update.getMessage().getFrom().getId();
            
            UserContext context = UserContext.get(userId);
            Long requestId = (Long) context.getData("requestId");
            String comment = update.getMessage().getText();
            
            if (requestId == null) {
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
                
                User admin = userOpt.get();
                
                requestService.rejectRequest(requestId, admin, comment);
                
                String message = String.format(
                        "❌ Tashkilotchi so'rovi rad etildi!\n\n" +
                        "Sabab: %s\n\n" +
                        "Foydalanuvchiga xabar yuboriladi.",
                        comment
                );
                
                bot.sendMessage(chatId, message);
                
                log.info("Organizer request {} rejected by admin {} with comment: {}", 
                         requestId, userId, comment);
                
            } catch (IllegalArgumentException e) {
                bot.sendMessage(chatId, "❌ So'rov topilmadi.");
            } catch (Exception e) {
                log.error("Error rejecting organizer request", e);
                bot.sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
            } finally {
                context.clearData();
            }
        }
    }

    @Override
    public String getCommandName() {
        return "/rejectorganizer";
    }
}
