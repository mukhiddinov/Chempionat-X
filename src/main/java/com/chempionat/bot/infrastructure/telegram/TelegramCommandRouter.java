package com.chempionat.bot.infrastructure.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramCommandRouter {

    private final List<TelegramCommand> commands;
    private final Map<String, TelegramCommand> commandMap = new HashMap<>();

    public void init() {
        commands.forEach(cmd -> {
            commandMap.put(cmd.getCommandName(), cmd);
            log.info("Registered command: {}", cmd.getCommandName());
        });
    }

    public void handleUpdate(Update update, TelegramBot bot) {
        if (!commandMap.isEmpty() && commandMap.size() != commands.size()) {
            init();
        }

        String messageText = update.getMessage().getText();
        String command = extractCommand(messageText);

        TelegramCommand telegramCommand = commandMap.get(command);
        if (telegramCommand != null) {
            log.debug("Executing command: {} for user: {}", command, update.getMessage().getFrom().getId());
            telegramCommand.execute(update, bot);
        } else {
            log.debug("Unknown command: {}", command);
            bot.sendMessage(update.getMessage().getChatId(), 
                "Unknown command. Available commands: /start, /profile");
        }
    }

    private String extractCommand(String messageText) {
        if (messageText.startsWith("/")) {
            int spaceIndex = messageText.indexOf(' ');
            if (spaceIndex > 0) {
                return messageText.substring(0, spaceIndex);
            }
            return messageText;
        }
        return messageText;
    }
}
