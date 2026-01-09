package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.event.UserStartedEvent;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartCommand implements TelegramCommand {

    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.getMessage().getChatId();
        org.telegram.telegrambots.meta.api.objects.User telegramUser = update.getMessage().getFrom();

        try {
            User user = userService.getOrCreateUser(
                    telegramUser.getId(),
                    telegramUser.getUserName(),
                    telegramUser.getFirstName(),
                    telegramUser.getLastName()
            );

            String welcomeMessage = String.format(
                    "Welcome to Chempionat-X, %s! 🎮⚽\n\n" +
                    "I'm your tournament management bot. Here's what I can help you with:\n\n" +
                    "/start - Start interaction with the bot\n" +
                    "/profile - View your profile\n\n" +
                    "More features coming soon!",
                    user.getFirstName() != null ? user.getFirstName() : "Player"
            );

            bot.sendMessage(chatId, welcomeMessage);

            // Publish event for notification
            eventPublisher.publishEvent(new UserStartedEvent(this, user));
            
            log.info("User started: telegramId={}, username={}", telegramUser.getId(), telegramUser.getUserName());
        } catch (Exception e) {
            log.error("Error executing start command", e);
            bot.sendMessage(chatId, "Sorry, an error occurred. Please try again later.");
        }
    }

    @Override
    public String getCommandName() {
        return "/start";
    }
}
