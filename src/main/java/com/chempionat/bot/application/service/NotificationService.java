package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.UserRepository;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;

@Slf4j
@Service
public class NotificationService {

    private final UserRepository userRepository;
    private final TelegramBot telegramBot;

    // Use @Lazy to break circular dependency
    public NotificationService(UserRepository userRepository, @Lazy TelegramBot telegramBot) {
        this.userRepository = userRepository;
        this.telegramBot = telegramBot;
    }

    /**
     * Send message to all admins
     */
    public void notifyAdmins(String message) {
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        
        for (User admin : admins) {
            try {
                telegramBot.sendMessage(admin.getTelegramId(), message);
                log.debug("Notification sent to admin: {}", admin.getTelegramId());
            } catch (Exception e) {
                log.error("Failed to send notification to admin {}", admin.getTelegramId(), e);
            }
        }
    }

    /**
     * Send message with inline keyboard to all admins
     */
    public void notifyAdminsWithKeyboard(String message, InlineKeyboardMarkup keyboard) {
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        
        for (User admin : admins) {
            try {
                telegramBot.sendMessage(admin.getTelegramId(), message, keyboard);
                log.debug("Notification with keyboard sent to admin: {}", admin.getTelegramId());
            } catch (Exception e) {
                log.error("Failed to send notification to admin {}", admin.getTelegramId(), e);
            }
        }
    }

    /**
     * Send message to specific user
     */
    public void notifyUser(Long telegramId, String message) {
        try {
            telegramBot.sendMessage(telegramId, message);
            log.debug("Notification sent to user: {}", telegramId);
        } catch (Exception e) {
            log.error("Failed to send notification to user {}", telegramId, e);
        }
    }

    /**
     * Send message with inline keyboard to specific user
     */
    public void notifyUserWithKeyboard(Long telegramId, String message, InlineKeyboardMarkup keyboard) {
        try {
            telegramBot.sendMessage(telegramId, message, keyboard);
            log.debug("Notification with keyboard sent to user: {}", telegramId);
        } catch (Exception e) {
            log.error("Failed to send notification to user {}", telegramId, e);
        }
    }

    /**
     * Send photo with caption and keyboard to specific user
     */
    public void notifyUserWithPhoto(Long telegramId, String photoFileId, String caption, InlineKeyboardMarkup keyboard) {
        try {
            telegramBot.sendPhoto(telegramId, photoFileId, caption);
            // Send keyboard in separate message since photo caption doesn't support inline keyboards well
            if (keyboard != null) {
                telegramBot.sendMessage(telegramId, "Tasdiqlash:", keyboard);
            }
            log.debug("Photo notification sent to user: {}", telegramId);
        } catch (Exception e) {
            log.error("Failed to send photo notification to user {}", telegramId, e);
        }
    }
}
