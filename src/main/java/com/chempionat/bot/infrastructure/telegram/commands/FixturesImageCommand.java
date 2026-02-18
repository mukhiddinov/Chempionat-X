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

/**
 * Command to display tournament fixtures/rounds as a PNG image with pagination.
 * Callback format: fixturesimg:{tournamentId}:{page}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixturesImageCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final MatchRepository matchRepository;
    private final ImageCacheService imageCacheService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId;
        Long tournamentId = null;
        int page = 0;
        Integer messageIdToDelete = null;

        if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            // Extract tournament ID from command: /fixturesimg <id>
            String[] parts = messageText.split(" ");
            if (parts.length < 2) {
                bot.sendMessage(chatId, "❌ Turnir ID ni kiriting: /fixturesimg <id>");
                return;
            }

            try {
                tournamentId = Long.parseLong(parts[1]);
                if (parts.length > 2) {
                    page = Integer.parseInt(parts[2]);
                }
            } catch (NumberFormatException e) {
                bot.sendMessage(chatId, "❌ Noto'g'ri turnir ID formati.");
                return;
            }

        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            messageIdToDelete = update.getCallbackQuery().getMessage().getMessageId();
            String callbackData = update.getCallbackQuery().getData();

            // Format: fixturesimg:{tournamentId}:{page} OR schedule:{tournamentId} (legacy) OR rounds:{tournamentId} (legacy)
            if (callbackData.startsWith("fixturesimg:")) {
                String[] parts = callbackData.split(":");
                if (parts.length >= 2) {
                    tournamentId = Long.parseLong(parts[1]);
                    if (parts.length >= 3) {
                        page = Integer.parseInt(parts[2]);
                    }
                }
            } else if (callbackData.startsWith("schedule:") || callbackData.startsWith("rounds:")) {
                // Legacy callback format: schedule:{tournamentId} or rounds:{tournamentId}
                String[] parts = callbackData.split(":");
                if (parts.length >= 2) {
                    try {
                        tournamentId = Long.parseLong(parts[1]);
                    } catch (NumberFormatException e) {
                        // Ignore - might be rounds:1 which is for number of rounds selection
                        return;
                    }
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
            List<Match> matches = matchRepository.findByTournament(tournament);

            if (matches.isEmpty()) {
                bot.sendMessage(chatId, "📅 Bu turnirda hali o'yinlar yaratilmagan.\n\n" +
                        "Admin turnirni /starttournament " + tournamentId + " buyrug'i bilan boshlashi kerak.");
                return;
            }

            // Sort matches by round then by id
            matches.sort(Comparator.comparing(Match::getRound, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(Match::getId));

            int totalPages = imageCacheService.getFixturesTotalPages(matches);
            page = Math.max(0, Math.min(page, totalPages - 1));

            // Get latest update time for cache key
            LocalDateTime latestUpdate = imageCacheService.getLatestMatchUpdate(matches);

            // Generate image
            byte[] imageData = imageCacheService.getFixturesImage(tournament, matches, page, latestUpdate);

            // Generate caption with match count
            String caption = imageCacheService.getFixturesCaption(matches, null);

            // Create pagination keyboard
            InlineKeyboardMarkup keyboard = createPaginationKeyboard(tournamentId, page, totalPages);

            // Delete old message if this is a callback (pagination)
            if (messageIdToDelete != null) {
                bot.deleteMessage(chatId, messageIdToDelete);
            }

            // Send image with caption
            String filename = "fixtures_" + tournamentId + "_" + page + ".png";
            bot.sendPhoto(chatId, imageData, filename, caption, keyboard);

        } catch (Exception e) {
            log.error("Error showing fixtures image for tournament {}", tournamentId, e);
            bot.sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    private InlineKeyboardMarkup createPaginationKeyboard(Long tournamentId, int currentPage, int totalPages) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Pagination row
        if (totalPages > 1) {
            List<InlineKeyboardButton> paginationRow = new ArrayList<>();

            // Previous button
            if (currentPage > 0) {
                InlineKeyboardButton prevButton = InlineKeyboardButton.builder()
                        .text("⬅️ Oldingi")
                        .callbackData("fixturesimg:" + tournamentId + ":" + (currentPage - 1))
                        .build();
                paginationRow.add(prevButton);
            }

            // Page indicator
            InlineKeyboardButton pageButton = InlineKeyboardButton.builder()
                    .text((currentPage + 1) + "/" + totalPages)
                    .callbackData("noop")
                    .build();
            paginationRow.add(pageButton);

            // Next button
            if (currentPage < totalPages - 1) {
                InlineKeyboardButton nextButton = InlineKeyboardButton.builder()
                        .text("Keyingi ➡️")
                        .callbackData("fixturesimg:" + tournamentId + ":" + (currentPage + 1))
                        .build();
                paginationRow.add(nextButton);
            }

            rows.add(paginationRow);
        }

        // Back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .text("⬅️ Ortga")
                .callbackData("view_tournament:" + tournamentId)
                .build();
        backRow.add(backButton);
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    @Override
    public String getCommandName() {
        return "/fixturesimg";
    }
}
