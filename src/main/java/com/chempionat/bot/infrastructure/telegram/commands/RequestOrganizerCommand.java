package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.OrganizerRequestService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.model.OrganizerRequest;
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
public class RequestOrganizerCommand implements TelegramCommand {

    private final OrganizerRequestService requestService;
    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (!update.hasMessage()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();

        try {
            Optional<User> userOpt = userService.getUserByTelegramId(userId);
            if (userOpt.isEmpty()) {
                bot.sendMessage(chatId, "Foydalanuvchi topilmadi. /start buyrug'ini yuboring.");
                return;
            }

            User user = userOpt.get();
            OrganizerRequest request = requestService.submitRequest(user);

            String message = "✅ Tashkilotchi bo'lish uchun so'rovingiz yuborildi!\n\n" +
                    "Admin tomonidan ko'rib chiqilishi kutilmoqda.\n" +
                    "Siz xabar olasiz.";

            bot.sendMessage(chatId, message);

            // Notify admin (we'll implement this in admin notification command)
            log.info("Organizer request {} submitted by user {}", request.getId(), userId);

        } catch (IllegalStateException e) {
            bot.sendMessage(chatId, "❌ " + e.getMessage());
        } catch (Exception e) {
            log.error("Error processing organizer request", e);
            bot.sendMessage(chatId, "Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    @Override
    public String getCommandName() {
        return "/requestorganizer";
    }
}
