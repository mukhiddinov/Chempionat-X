package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId;
        Long tournamentId = null;

        if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            
            // Extract tournament ID from command
            String[] parts = messageText.split(" ");
            if (parts.length < 2) {
                bot.sendMessage(chatId, "❌ Turnir ID ni kiriting: /schedule <id>");
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
            
            if (callbackData.startsWith("schedule:")) {
                tournamentId = Long.parseLong(callbackData.split(":")[1]);
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
                String message = "❌ Turnir topilmadi.";
                if (update.hasCallbackQuery()) {
                    bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(), message);
                } else {
                    bot.sendMessage(chatId, message);
                }
                return;
            }

            Tournament tournament = tournamentOpt.get();
            List<Match> matches = tournamentService.getTournamentMatches(tournamentId);

            if (matches.isEmpty()) {
                String message = "📅 Bu turnirda hali o'yinlar yaratilmagan.\n\n" +
                        "Admin turnirni /starttournament " + tournamentId + " buyrug'i bilan boshlashi kerak.";
                if (update.hasCallbackQuery()) {
                    bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(), message);
                } else {
                    bot.sendMessage(chatId, message);
                }
                return;
            }

            // Sort matches by scheduled time
            matches.sort(Comparator.comparing(Match::getScheduledTime));

            StringBuilder message = new StringBuilder();
            message.append("📅 ").append(tournament.getName()).append(" - Kalendar\n\n");

            String currentRound = null;
            for (Match match : matches) {
                // Group by round if available
                String round = match.getRound() != null ? "Tur " + match.getRound() : "O'yin";
                if (!round.equals(currentRound)) {
                    currentRound = round;
                    message.append("\n🔹 ").append(currentRound).append("\n");
                }

                message.append(String.format("⚽ %s vs %s\n",
                        match.getHomeTeam().getName(),
                        match.getAwayTeam().getName()));
                
                if (match.getScheduledTime() != null) {
                    message.append("   📆 ").append(match.getScheduledTime().format(DATE_FORMATTER)).append("\n");
                }

                if (match.getHomeScore() != null && match.getAwayScore() != null) {
                    message.append("   🏆 Natija: ")
                           .append(match.getHomeScore())
                           .append(":")
                           .append(match.getAwayScore())
                           .append("\n");
                } else {
                    message.append("   ⏳ Kutilmoqda\n");
                }
                
                message.append("\n");
            }

            // Split message if too long (Telegram limit is 4096 characters)
            String fullMessage = message.toString();
            if (fullMessage.length() > 4000) {
                // Send in chunks
                int chunkSize = 4000;
                for (int i = 0; i < fullMessage.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, fullMessage.length());
                    String chunk = fullMessage.substring(i, end);
                    if (update.hasCallbackQuery() && i == 0) {
                        bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(), chunk);
                    } else {
                        bot.sendMessage(chatId, chunk);
                    }
                }
            } else {
                if (update.hasCallbackQuery()) {
                    bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(), fullMessage);
                } else {
                    bot.sendMessage(chatId, fullMessage);
                }
            }

        } catch (Exception e) {
            log.error("Error showing schedule", e);
            bot.sendMessage(chatId, "Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    @Override
    public String getCommandName() {
        return "/schedule";
    }
}
