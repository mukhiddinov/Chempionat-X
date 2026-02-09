package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.TeamRepository;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteTournamentCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (!update.hasCallbackQuery()) {
            return;
        }

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();

        if (callbackData.startsWith("delete_tournament:")) {
            showConfirmation(update, bot);
        } else if (callbackData.startsWith("confirm_delete:")) {
            performDelete(update, bot);
        } else if (callbackData.startsWith("cancel_delete:")) {
            cancelDelete(update, bot);
        }
    }

    private void showConfirmation(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long tournamentId = Long.parseLong(callbackData.split(":")[1]);

        try {
            Tournament tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            long teamsCount = teamRepository.findByTournament(tournament).size();
            long matchesCount = matchRepository.findByTournament(tournament).size();

            String message = "⚠️ Turnirni o'chirish\n\n" +
                    "🏆 " + tournament.getName() + "\n\n" +
                    "📊 Ma'lumotlar:\n" +
                    "👥 Ishtirokchilar: " + teamsCount + "\n" +
                    "⚽ O'yinlar: " + matchesCount + "\n\n" +
                    "❗️ Diqqat: Bu amal qaytarib bo'lmaydi!\n" +
                    "Barcha o'yinlar va natijalar o'chib ketadi.\n\n" +
                    "Davom etishni xohlaysizmi?";

            InlineKeyboardMarkup keyboard = createConfirmationKeyboard(tournamentId);
            bot.editMessage(chatId, messageId, message, keyboard);

        } catch (Exception e) {
            log.error("Error showing delete confirmation", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    private InlineKeyboardMarkup createConfirmationKeyboard(Long tournamentId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Confirm button
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
        confirmBtn.setText("✅ Ha, o'chirish");
        confirmBtn.setCallbackData("confirm_delete:" + tournamentId);
        row1.add(confirmBtn);
        rows.add(row1);

        // Cancel button
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText("❌ Yo'q, bekor qilish");
        cancelBtn.setCallbackData("cancel_delete:" + tournamentId);
        row2.add(cancelBtn);
        rows.add(row2);

        markup.setKeyboard(rows);
        return markup;
    }

    private void performDelete(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long tournamentId = Long.parseLong(callbackData.split(":")[1]);
        Long userId = update.getCallbackQuery().getFrom().getId();

        try {
            Tournament tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            String tournamentName = tournament.getName();

            // Delete cascade: matches first, then teams, then tournament
            matchRepository.deleteAll(matchRepository.findByTournament(tournament));
            teamRepository.deleteAll(teamRepository.findByTournament(tournament));
            tournamentService.deleteTournament(tournamentId);

            String message = "✅ Turnir o'chirildi!\n\n" +
                    "🏆 " + tournamentName + "\n\n" +
                    "Barcha ma'lumotlar o'chirildi.";

            bot.editMessage(chatId, messageId, message);
            
            log.info("Tournament {} deleted by user {}", tournamentId, userId);

        } catch (Exception e) {
            log.error("Error deleting tournament", e);
            bot.editMessage(chatId, messageId, "❌ O'chirishda xatolik yuz berdi");
        }
    }

    private void cancelDelete(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long tournamentId = Long.parseLong(callbackData.split(":")[1]);

        try {
            Tournament tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            String message = "❌ Bekor qilindi\n\n" +
                    "🏆 " + tournament.getName() + " o'chirilmadi.";

            bot.editMessage(chatId, messageId, message);

        } catch (Exception e) {
            log.error("Error canceling delete", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    @Override
    public String getCommandName() {
        return "/deletetournament";
    }
}
