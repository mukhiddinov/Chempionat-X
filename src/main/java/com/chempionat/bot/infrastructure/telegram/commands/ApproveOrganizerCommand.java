package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.OrganizerRequestService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.User;
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
public class ApproveOrganizerCommand implements TelegramCommand {

    private final OrganizerRequestService requestService;
    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (!update.hasCallbackQuery()) {
            return;
        }

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        String callbackData = update.getCallbackQuery().getData();

        // Check if user is admin
        Optional<User> userOpt = userService.getUserByTelegramId(userId);
        if (userOpt.isEmpty() || userOpt.get().getRole() != Role.ADMIN) {
            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    "❌ Faqat adminlar tashkilotchi so'rovini qabul qilishi mumkin.");
            return;
        }

        try {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            User admin = userOpt.get();

            requestService.approveRequest(requestId, admin);

            String message = "✅ Tashkilotchi so'rovi qabul qilindi!\n\n" +
                           "Foydalanuvchi endi tashkilotchi roliga ega.\n" +
                           "Qabul qilgan: " + (admin.getUsername() != null ? 
                           "@" + admin.getUsername() : admin.getFirstName());

            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(), message);

            log.info("Organizer request {} approved by admin {}", requestId, userId);

        } catch (IllegalArgumentException e) {
            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    "❌ So'rov topilmadi.");
        } catch (IllegalStateException e) {
            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    "❌ " + e.getMessage());
        } catch (Exception e) {
            log.error("Error approving organizer request", e);
            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    "❌ Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    @Override
    public String getCommandName() {
        return "/approveorganizer";
    }
}
