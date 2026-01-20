package com.chempionat.bot.infrastructure.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final TelegramCommandRouter commandRouter;

    public TelegramBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            TelegramCommandRouter commandRouter) {
        super(botToken);
        this.botUsername = botUsername;
        this.commandRouter = commandRouter;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                commandRouter.handleUpdate(update, this);
            } else if (update.hasCallbackQuery()) {
                commandRouter.handleCallbackQuery(update, this);
            } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
                commandRouter.handlePhotoMessage(update, this);
            }
        } catch (Exception e) {
            log.error("Error processing update: {}", update, e);
            if (update.hasMessage()) {
                sendMessage(update.getMessage().getChatId(), "An error occurred. Please try again.");
            }
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();
        try {
            execute(message);
            log.debug("Message sent to chat {}: {}", chatId, text);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat {}", chatId, e);
        }
    }

    public void sendMessage(Long chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(keyboard)
                .build();
        try {
            execute(message);
            log.debug("Message with keyboard sent to chat {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat {}", chatId, e);
        }
    }

    public void sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(keyboard)
                .build();
        try {
            execute(message);
            log.debug("Message with inline keyboard sent to chat {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat {}", chatId, e);
        }
    }

    public void editMessage(Long chatId, Integer messageId, String text) {
        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(text)
                .build();
        try {
            execute(editMessage);
            log.debug("Message edited in chat {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message in chat {}", chatId, e);
        }
    }

    public void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(text)
                .replyMarkup(keyboard)
                .build();
        try {
            execute(editMessage);
            log.debug("Message with keyboard edited in chat {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message in chat {}", chatId, e);
        }
    }

    public void sendPhoto(Long chatId, String photoFileId, String caption) {
        SendPhoto photo = SendPhoto.builder()
                .chatId(chatId.toString())
                .photo(new InputFile(photoFileId))
                .caption(caption)
                .build();
        try {
            execute(photo);
            log.debug("Photo sent to chat {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send photo to chat {}", chatId, e);
        }
    }
}
