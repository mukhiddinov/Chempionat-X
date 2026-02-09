package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.UserRepository;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import com.chempionat.bot.infrastructure.telegram.util.PaginationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizersListCommand implements TelegramCommand {

    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = extractChatId(update);
        Long userId = extractUserId(update);

        // Check if user is admin
        Optional<User> userOpt = userService.getUserByTelegramId(userId);
        if (userOpt.isEmpty() || userOpt.get().getRole() != Role.ADMIN) {
            bot.sendMessage(chatId, "❌ Faqat adminlar ushbu bo'limga kirishi mumkin");
            return;
        }

        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            if (callbackData.startsWith("page:organizers:")) {
                handlePagination(update, bot);
            }
        } else {
            showOrganizersList(bot, chatId, 0);
        }
    }

    private void showOrganizersList(TelegramBot bot, Long chatId, int page) {
        List<User> organizers = userRepository.findByRole(Role.ORGANIZER);

        if (organizers.isEmpty()) {
            bot.sendMessage(chatId, "📭 Hozircha tashkilotchilar yo'q");
            return;
        }

        String message = "👥 Tashkilotchilar ro'yxati\n\n" +
                "Boshqarish uchun tashkilotchini tanlang:";

        InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboard(
                organizers,
                page,
                10,
                this::formatOrganizerButton,
                u -> "manage_organizer:" + u.getId(),
                "organizers"
        );

        bot.sendMessage(chatId, message, keyboard);
    }

    private String formatOrganizerButton(User user) {
        if (user.getUsername() != null) {
            return "👤 @" + user.getUsername();
        } else {
            return "👤 " + user.getFirstName();
        }
    }

    private void handlePagination(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();

        try {
            int page = PaginationHelper.extractPageNumber(callbackData);

            List<User> organizers = userRepository.findByRole(Role.ORGANIZER);
            String message = "👥 Tashkilotchilar ro'yxati\n\nBoshqarish uchun tashkilotchini tanlang:";

            InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboard(
                    organizers,
                    page,
                    10,
                    this::formatOrganizerButton,
                    u -> "manage_organizer:" + u.getId(),
                    "organizers"
            );

            bot.editMessage(chatId, messageId, message, keyboard);

        } catch (Exception e) {
            log.error("Error handling pagination", e);
        }
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
        return "/organizers";
    }
}
