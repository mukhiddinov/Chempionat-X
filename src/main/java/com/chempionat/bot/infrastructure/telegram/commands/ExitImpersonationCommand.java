package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.infrastructure.telegram.KeyboardFactory;
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
public class ExitImpersonationCommand implements TelegramCommand {

    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = extractChatId(update);
        Long userId = extractUserId(update);

        // Verify user is admin
        Optional<User> userOpt = userService.getUserByTelegramId(userId);
        if (userOpt.isEmpty() || userOpt.get().getRole() != Role.ADMIN) {
            bot.sendMessage(chatId, "❌ Bu buyruq faqat adminlar uchun");
            return;
        }

        UserContext context = UserContext.get(userId);
        
        if (!context.isImpersonating()) {
            bot.sendMessage(chatId, "ℹ️ Siz hozirda hech kimni impersonate qilmayapsiz", 
                    KeyboardFactory.createAdminMenu());
            return;
        }

        context.exitImpersonation();
        
        String message = "✅ Siz tashkilotchi profilidan chiqdingiz\n\n" +
                "Endi o'zingiz (admin) sifatida ishlayapsiz.";
        
        bot.sendMessage(chatId, message, KeyboardFactory.createAdminMenu());
        
        log.info("Admin {} exited impersonation via command/button", userId);
    }

    private Long extractChatId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        } else if (update.hasCallbackQuery()) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }

    private Long extractUserId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getFrom().getId();
        } else if (update.hasCallbackQuery()) {
            return update.getCallbackQuery().getFrom().getId();
        }
        return null;
    }

    @Override
    public String getCommandName() {
        return "/exitimpersonation";
    }
}
