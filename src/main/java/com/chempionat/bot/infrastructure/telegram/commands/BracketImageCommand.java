package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.BracketImageRenderer;
import com.chempionat.bot.application.service.SingleEliminationService;
import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Command to display tournament bracket as an image.
 * Works for PLAYOFF type tournaments only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BracketImageCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final SingleEliminationService singleEliminationService;
    private final BracketImageRenderer bracketImageRenderer;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId;
        Long tournamentId = null;

        if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            // Extract tournament ID from command: /bracket <id>
            String[] parts = messageText.split(" ");
            if (parts.length < 2) {
                bot.sendMessage(chatId, "❌ Turnir ID ni kiriting: /bracket <id>");
                return;
            }

            try {
                tournamentId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                bot.sendMessage(chatId, "❌ Noto'g'ri turnir ID formati.");
                return;
            }

        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            String callbackData = update.getCallbackQuery().getData();

            if (callbackData.startsWith("bracket:")) {
                try {
                    tournamentId = Long.parseLong(callbackData.split(":")[1]);
                } catch (Exception e) {
                    bot.sendMessage(chatId, "❌ Xatolik yuz berdi.");
                    return;
                }
            } else if (callbackData.startsWith("bracketimg:")) {
                try {
                    tournamentId = Long.parseLong(callbackData.split(":")[1]);
                } catch (Exception e) {
                    bot.sendMessage(chatId, "❌ Xatolik yuz berdi.");
                    return;
                }
            }
        } else {
            return;
        }

        if (tournamentId == null) {
            bot.sendMessage(chatId, "❌ Turnir ID topilmadi.");
            return;
        }

        try {
            Optional<Tournament> tournamentOpt = tournamentService.getTournamentById(tournamentId);
            if (tournamentOpt.isEmpty()) {
                bot.sendMessage(chatId, "❌ Turnir topilmadi.");
                return;
            }

            Tournament tournament = tournamentOpt.get();

            // Check if tournament is PLAYOFF type
            if (tournament.getType() != TournamentType.PLAYOFF) {
                bot.sendMessage(chatId, "ℹ️ Bracket faqat playoff turnirlar uchun mavjud.\n" +
                        "Bu turnir turi: " + tournament.getType());
                return;
            }

            // Get bracket matches
            List<Match> bracketMatches = singleEliminationService.getBracketTree(tournament);

            if (bracketMatches.isEmpty()) {
                bot.sendMessage(chatId, "ℹ️ Bracket hali yaratilmagan. Turnir boshlanganidan keyin ko'rinadi.");
                return;
            }

            // Render bracket image
            byte[] imageData = bracketImageRenderer.render(tournament.getName(), bracketMatches);
            String caption = bracketImageRenderer.generateCaption(tournament.getName(), bracketMatches);

            // Create keyboard with back button
            InlineKeyboardMarkup keyboard = createBackKeyboard(tournamentId);

            // Send image
            bot.sendPhoto(chatId, imageData, "bracket_" + tournamentId + ".png", caption);
            bot.sendMessage(chatId, "⬇️", keyboard);

            log.info("Bracket image sent for tournament {}", tournamentId);

        } catch (Exception e) {
            log.error("Error rendering bracket for tournament {}", tournamentId, e);
            bot.sendMessage(chatId, "❌ Bracket yaratishda xatolik yuz berdi.");
        }
    }

    private InlineKeyboardMarkup createBackKeyboard(Long tournamentId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
                .text("🔄 Yangilash")
                .callbackData("bracketimg:" + tournamentId)
                .build());
        row.add(InlineKeyboardButton.builder()
                .text("⬅️ Ortga")
                .callbackData("view_tournament:" + tournamentId)
                .build());
        rows.add(row);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    @Override
    public String getCommandName() {
        return "/bracket";
    }
}
