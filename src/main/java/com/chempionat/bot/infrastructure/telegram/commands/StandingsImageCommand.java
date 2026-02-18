package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.ImageCacheService;
import com.chempionat.bot.application.service.TeamStanding;
import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Command to display tournament standings as a PNG image with pagination.
 * Callback format: standingsimg:{tournamentId}:{page}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandingsImageCommand implements TelegramCommand {

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

            // Extract tournament ID from command: /standingsimg <id>
            String[] parts = messageText.split(" ");
            if (parts.length < 2) {
                bot.sendMessage(chatId, "❌ Turnir ID ni kiriting: /standingsimg <id>");
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

            // Format: standingsimg:{tournamentId}:{page} OR standings:{tournamentId} (legacy)
            if (callbackData.startsWith("standingsimg:")) {
                String[] parts = callbackData.split(":");
                if (parts.length >= 2) {
                    tournamentId = Long.parseLong(parts[1]);
                    if (parts.length >= 3) {
                        page = Integer.parseInt(parts[2]);
                    }
                }
            } else if (callbackData.startsWith("standings:")) {
                // Legacy callback format: standings:{tournamentId}
                String[] parts = callbackData.split(":");
                if (parts.length >= 2) {
                    tournamentId = Long.parseLong(parts[1]);
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
            List<Team> teams = tournamentService.getTournamentTeams(tournamentId);
            List<Match> matches = matchRepository.findByTournamentAndHomeScoreIsNotNull(tournament);

            if (teams.isEmpty()) {
                bot.sendMessage(chatId, "📊 Bu turnirda hali ishtirokchilar yo'q.");
                return;
            }

            // Calculate standings
            List<TeamStanding> standings = calculateStandings(teams, matches);

            int totalPages = imageCacheService.getStandingsTotalPages(standings.size());
            page = Math.max(0, Math.min(page, totalPages - 1));

            // Generate image
            byte[] imageData = imageCacheService.getStandingsImage(tournament, standings, page);

            // Generate caption with top 3 teams
            String caption = imageCacheService.getStandingsCaption(standings);

            // Create pagination keyboard
            InlineKeyboardMarkup keyboard = createPaginationKeyboard(tournamentId, page, totalPages);

            // Delete old message if this is a callback (pagination)
            if (messageIdToDelete != null) {
                bot.deleteMessage(chatId, messageIdToDelete);
            }

            // Send image with caption
            String filename = "standings_" + tournamentId + "_" + page + ".png";
            bot.sendPhoto(chatId, imageData, filename, caption, keyboard);

        } catch (Exception e) {
            log.error("Error showing standings image for tournament {}", tournamentId, e);
            bot.sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    private List<TeamStanding> calculateStandings(List<Team> teams, List<Match> matches) {
        Map<Long, TeamStanding> standingsMap = new HashMap<>();

        // Initialize standings for all teams
        for (Team team : teams) {
            standingsMap.put(team.getId(), new TeamStanding(team.getId(), team.getName()));
        }

        // Calculate stats from matches
        for (Match match : matches) {
            if (match.getHomeScore() == null || match.getAwayScore() == null) {
                continue;
            }

            TeamStanding homeStanding = standingsMap.get(match.getHomeTeam().getId());
            TeamStanding awayStanding = standingsMap.get(match.getAwayTeam().getId());

            if (homeStanding == null || awayStanding == null) {
                continue;
            }

            homeStanding.addMatch(match.getHomeScore(), match.getAwayScore());
            awayStanding.addMatch(match.getAwayScore(), match.getHomeScore());
        }

        // Sort standings
        return standingsMap.values().stream()
                .sorted()
                .collect(Collectors.toList());
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
                        .callbackData("standingsimg:" + tournamentId + ":" + (currentPage - 1))
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
                        .callbackData("standingsimg:" + tournamentId + ":" + (currentPage + 1))
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
        return "/standingsimg";
    }
}
