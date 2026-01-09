package com.chempionat.bot.infrastructure.telegram;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface TelegramCommand {
    void execute(Update update, TelegramBot bot);
    String getCommandName();
}
