package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.TournamentRepository;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import com.chempionat.bot.infrastructure.telegram.util.PaginationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MyTournamentsCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final UserService userService;
    private final TournamentRepository tournamentRepository;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId;
        Long userId;
        int currentPage = 0;

        if (update.hasMessage()) {
            chatId = update.getMessage().getChatId();
            userId = update.getMessage().getFrom().getId();
        } else if (update.hasCallbackQuery()) {
            CallbackQuery callback = update.getCallbackQuery();
            chatId = callback.getMessage().getChatId();
            userId = callback.getFrom().getId();
            
            // Handle pagination
            if (callback.getData().startsWith("page:my_tournaments:")) {
                currentPage = PaginationHelper.extractPageNumber(callback.getData());
            } else if (callback.getData().equals("my_tournaments")) {
                currentPage = 0;
            }
        } else {
            return;
        }

        try {
            Optional<User> userOpt = userService.getUserByTelegramId(userId);
            if (userOpt.isEmpty()) {
                bot.sendMessage(chatId, "Foydalanuvchi topilmadi. /start buyrug'ini yuboring.");
                return;
            }

            User user = userOpt.get();
            List<Tournament> tournaments = tournamentRepository.findTournamentsByPlayerId(user.getId());

            if (tournaments.isEmpty()) {
                bot.sendMessage(chatId, 
                        "📋 Siz hali turnirga qo'shilmagansiz.\n\n" +
                        "Turnirga qo'shilish uchun 🎯 Turnirga qo'shilish tugmasini bosing.");
                return;
            }

            String message = String.format("🏆 Sizning turnirlaringiz (%d ta):\n\n" +
                    "Tanlang:", tournaments.size());

            InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboard(
                    tournaments,
                    currentPage,
                    10,
                    t -> String.format("%s %s", 
                            getTypeEmoji(t.getType()), 
                            t.getName()),
                    t -> "view_tournament:" + t.getId(),
                    "my_tournaments"
            );

            if (update.hasCallbackQuery()) {
                bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(), message, keyboard);
            } else {
                bot.sendMessage(chatId, message, keyboard);
            }

            log.info("User {} viewed their tournaments, page {}", userId, currentPage);

        } catch (Exception e) {
            log.error("Error showing user tournaments", e);
            bot.sendMessage(chatId, "Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    private String getTypeEmoji(TournamentType type) {
        return type == TournamentType.LEAGUE ? "🏆" : "🥇";
    }

    @Override
    public String getCommandName() {
        return "/mytournaments";
    }
}
