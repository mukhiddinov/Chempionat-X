package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.ActorContextService;
import com.chempionat.bot.application.service.UserService;
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
public class ProfileCommand implements TelegramCommand {

    private final UserService userService;
    private final ActorContextService actorContextService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();

        try {
            // Use ActorContextService to get effective actor (respects impersonation)
            Optional<User> effectiveUserOpt = actorContextService.getEffectiveActor(telegramId);

            if (effectiveUserOpt.isPresent()) {
                User user = effectiveUserOpt.get();
                
                // Build profile message
                StringBuilder profileMessage = new StringBuilder();
                
                // Add impersonation indicator if admin is impersonating
                if (actorContextService.isImpersonating(telegramId)) {
                    profileMessage.append("🎭 Tashkilotchi profilini ko'rmoqdasiz\n\n");
                }
                
                profileMessage.append(String.format(
                        "👤 Your Profile\n\n" +
                        "Name: %s %s\n" +
                        "Username: @%s\n" +
                        "Role: %s\n" +
                        "Member since: %s",
                        user.getFirstName() != null ? user.getFirstName() : "",
                        user.getLastName() != null ? user.getLastName() : "",
                        user.getUsername() != null ? user.getUsername() : "N/A",
                        user.getRole(),
                        user.getCreatedAt().toLocalDate()
                ));
                
                bot.sendMessage(chatId, profileMessage.toString());
            } else {
                bot.sendMessage(chatId, "Profile not found. Please use /start first.");
            }
            
            log.debug("Profile viewed by user: {} (effective actor)", telegramId);
        } catch (Exception e) {
            log.error("Error executing profile command", e);
            bot.sendMessage(chatId, "Sorry, an error occurred. Please try again later.");
        }
    }

    @Override
    public String getCommandName() {
        return "/profile";
    }
}
