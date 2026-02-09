package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.OrganizerRequestService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.OrganizerRequest;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizerRequestsCommand implements TelegramCommand {

    private final OrganizerRequestService requestService;
    private final UserService userService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (!update.hasMessage()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();

        // Check if user is admin
        Optional<User> userOpt = userService.getUserByTelegramId(userId);
        if (userOpt.isEmpty() || userOpt.get().getRole() != Role.ADMIN) {
            bot.sendMessage(chatId, "❌ Faqat adminlar tashkilotchi so'rovlarini ko'rishi mumkin.");
            return;
        }

        try {
            List<OrganizerRequest> pendingRequests = requestService.getPendingRequests();

            if (pendingRequests.isEmpty()) {
                bot.sendMessage(chatId, "✅ Kutilayotgan tashkilotchi so'rovlari yo'q.");
                return;
            }

            for (OrganizerRequest request : pendingRequests) {
                User requestUser = request.getUser();
                
                StringBuilder message = new StringBuilder();
                message.append("📋 Tashkilotchi so'rovi\n\n");
                message.append(String.format("👤 Foydalanuvchi: %s\n",
                        requestUser.getUsername() != null ? 
                        "@" + requestUser.getUsername() : 
                        requestUser.getFirstName()));
                message.append(String.format("🆔 Telegram ID: %d\n", requestUser.getTelegramId()));
                message.append(String.format("📅 So'rov vaqti: %s\n",
                        request.getCreatedAt().format(DATE_FORMATTER)));

                // Create approval keyboard
                InlineKeyboardMarkup keyboard = createApprovalKeyboard(request.getId());

                bot.sendMessage(chatId, message.toString(), keyboard);
            }

            log.info("Sent {} pending organizer requests to admin {}", pendingRequests.size(), userId);

        } catch (Exception e) {
            log.error("Error showing organizer requests", e);
            bot.sendMessage(chatId, "Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    private InlineKeyboardMarkup createApprovalKeyboard(Long requestId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton approveButton = InlineKeyboardButton.builder()
                .text("✅ Qabul qilish")
                .callbackData("approveorganizer:" + requestId)
                .build();
        
        InlineKeyboardButton rejectButton = InlineKeyboardButton.builder()
                .text("❌ Rad etish")
                .callbackData("rejectorganizer:" + requestId)
                .build();

        row.add(approveButton);
        row.add(rejectButton);
        rows.add(row);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    @Override
    public String getCommandName() {
        return "/organizerrequests";
    }
}
