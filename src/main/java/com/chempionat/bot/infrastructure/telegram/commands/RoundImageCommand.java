package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.ImageCacheService;
import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Command to display fixtures for a specific round as PNG image with pagination.
 * Callback format: roundimg:{tournamentId}:{roundNumber}:{page}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoundImageCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final MatchRepository matchRepository;
    private final ImageCacheService imageCacheService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId;
        Long tournamentId = null;
        Integer roundNumber = null;
        int page = 0;
        Integer messageIdToDelete = null;

        if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            // Format: /roundimg <tournamentId> <roundNumber> [page]
            String[] parts = messageText.split(" ");
            if (parts.length < 3) {
                bot.sendMessage(chatId, "❌ Format: /roundimg <tournamentId> <roundNumber>");
                return;
            }

            try {
                tournamentId = Long.parseLong(parts[1]);
                roundNumber = Integer.parseInt(parts[2]);
                if (parts.length > 3) {
                    page = Integer.parseInt(parts[3]);
                }
            } catch (NumberFormatException e) {
                bot.sendMessage(chatId, "❌ Noto'g'ri format.");
                return;
            }

        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            messageIdToDelete = update.getCallbackQuery().getMessage().getMessageId();
            String callbackData = update.getCallbackQuery().getData();

            // Format: roundimg:{tournamentId}:{roundNumber}:{page}
            if (callbackData.startsWith("roundimg:")) {
                String[] parts = callbackData.split(":");
                if (parts.length >= 3) {
                    tournamentId = Long.parseLong(parts[1]);
                    roundNumber = Integer.parseInt(parts[2]);
                    if (parts.length >= 4) {
                        page = Integer.parseInt(parts[3]);
                    }
                }
            }
        } else {
            return;
        }

        if (tournamentId == null || roundNumber == null) {
            bot.sendMessage(chatId, "❌ Turnir ID yoki tur raqami topilmadi.");
            return;
        }

        try {
            Optional<Tournament> tournamentOpt = tournamentService.getTournamentById(tournamentId);
            if (tournamentOpt.isEmpty()) {
                bot.sendMessage(chatId, "❌ Turnir topilmadi.");
                return;
            }

            Tournament tournament = tournamentOpt.get();
            List<Match> roundMatches = matchRepository.findByTournamentAndRound(tournament, roundNumber);

            if (roundMatches.isEmpty()) {
                bot.sendMessage(chatId, "📅 Bu turda o'yinlar yo'q.");
                return;
            }

            // Sort by scheduled time
            roundMatches.sort(Comparator.comparing(
                    m -> m.getScheduledTime() != null ? m.getScheduledTime() : LocalDateTime.MAX));

            // Get max rounds for navigation
            int maxRound = getMaxRound(tournament);

            int totalPages = Math.max(1, (int) Math.ceil((double) roundMatches.size() / 12));
            page = Math.max(0, Math.min(page, totalPages - 1));

            // Get latest update time for cache key
            LocalDateTime latestUpdate = imageCacheService.getLatestMatchUpdate(roundMatches);

            // Generate image for this round
            byte[] imageData = imageCacheService.getFixturesRoundImage(
                    tournament, roundNumber, roundMatches, page, latestUpdate);

            // Generate caption
            String caption = imageCacheService.getFixturesCaption(roundMatches, roundNumber);

            // Create navigation keyboard
            InlineKeyboardMarkup keyboard = createNavigationKeyboard(
                    tournamentId, roundNumber, page, totalPages, maxRound);

            // Delete old message if this is a callback
            if (messageIdToDelete != null) {
                bot.deleteMessage(chatId, messageIdToDelete);
            }

            // Send image
            String filename = "round_" + tournamentId + "_" + roundNumber + "_" + page + ".png";
            bot.sendPhoto(chatId, imageData, filename, caption, keyboard);

        } catch (Exception e) {
            log.error("Error showing round image for tournament {} round {}", tournamentId, roundNumber, e);
            bot.sendMessage(chatId, "❌ Xatolik yuz berdi.");
        }
    }

    private int getMaxRound(Tournament tournament) {
        List<Match> allMatches = matchRepository.findByTournament(tournament);
        return allMatches.stream()
                .filter(m -> m.getRound() != null)
                .mapToInt(Match::getRound)
                .max()
                .orElse(1);
    }

    private InlineKeyboardMarkup createNavigationKeyboard(
            Long tournamentId, int currentRound, int currentPage, int totalPages, int maxRound) {
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Page navigation (if multiple pages)
        if (totalPages > 1) {
            List<InlineKeyboardButton> pageRow = new ArrayList<>();

            if (currentPage > 0) {
                pageRow.add(InlineKeyboardButton.builder()
                        .text("⬅️ Prev Page")
                        .callbackData("roundimg:" + tournamentId + ":" + currentRound + ":" + (currentPage - 1))
                        .build());
            }

            pageRow.add(InlineKeyboardButton.builder()
                    .text((currentPage + 1) + "/" + totalPages)
                    .callbackData("noop")
                    .build());

            if (currentPage < totalPages - 1) {
                pageRow.add(InlineKeyboardButton.builder()
                        .text("Next Page ➡️")
                        .callbackData("roundimg:" + tournamentId + ":" + currentRound + ":" + (currentPage + 1))
                        .build());
            }

            rows.add(pageRow);
        }

        // Round navigation
        List<InlineKeyboardButton> roundRow = new ArrayList<>();

        if (currentRound > 1) {
            roundRow.add(InlineKeyboardButton.builder()
                    .text("◀️ Round " + (currentRound - 1))
                    .callbackData("roundimg:" + tournamentId + ":" + (currentRound - 1) + ":0")
                    .build());
        }

        roundRow.add(InlineKeyboardButton.builder()
                .text("📋 Round " + currentRound)
                .callbackData("noop")
                .build());

        if (currentRound < maxRound) {
            roundRow.add(InlineKeyboardButton.builder()
                    .text("Round " + (currentRound + 1) + " ▶️")
                    .callbackData("roundimg:" + tournamentId + ":" + (currentRound + 1) + ":0")
                    .build());
        }

        rows.add(roundRow);

        // Back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(InlineKeyboardButton.builder()
                .text("🔙 All Fixtures")
                .callbackData("fixturesimg:" + tournamentId + ":0")
                .build());
        backRow.add(InlineKeyboardButton.builder()
                .text("⬅️ Tournament")
                .callbackData("view_tournament:" + tournamentId)
                .build());
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    @Override
    public String getCommandName() {
        return "/roundimg";
    }
}
