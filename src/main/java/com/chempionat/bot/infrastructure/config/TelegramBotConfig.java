package com.chempionat.bot.infrastructure.config;

import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@Profile("!test")
public class TelegramBotConfig {

    private final TelegramBot telegramBot;

    public TelegramBotConfig(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    @EventListener({ContextRefreshedEvent.class})
    public void init() throws TelegramApiException {
        log.info("Initializing Telegram Bot API...");
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            telegramBotsApi.registerBot(telegramBot);
            log.info("Telegram bot registered successfully: {}", telegramBot.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot", e);
            throw e;
        }
    }
}
