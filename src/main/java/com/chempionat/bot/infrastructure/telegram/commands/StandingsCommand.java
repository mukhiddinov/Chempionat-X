package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.SingleEliminationService;
import com.chempionat.bot.application.service.TeamStanding;
import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.domain.enums.TournamentType;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class StandingsCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final MatchRepository matchRepository;
    private final SingleEliminationService singleEliminationService;

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
                bot.sendMessage(chatId, "❌ Turnir ID ni kiriting: /standings <id>");
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
            
            if (callbackData.startsWith("standings:")) {
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
            List<Team> teams = tournamentService.getTournamentTeams(tournamentId);
            List<Match> matches = matchRepository.findByTournamentAndHomeScoreIsNotNull(tournament);

            if (teams.isEmpty()) {
                String message = "📊 Bu turnirda hali ishtirokchilar yo'q.";
                if (update.hasCallbackQuery()) {
                    bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(), message);
                } else {
                    bot.sendMessage(chatId, message);
                }
                return;
            }

            // Calculate standings based on tournament type
            List<TeamStanding> standings;
            boolean isPlayoff = tournament.getType() == TournamentType.PLAYOFF;
            
            if (isPlayoff) {
                // Use bracket-based placement for single elimination
                standings = singleEliminationService.calculateBracketPlacements(tournament);
            } else {
                // Use league-style points-based standings
                standings = calculateStandings(teams, matches);
            }

            StringBuilder message = new StringBuilder();
            message.append("📊 ").append(tournament.getName()).append(" - Jadval\n\n");
            
            if (isPlayoff) {
                // Bracket-based display for playoffs
                message.append("```\n");
                message.append(String.format("%-3s %-20s\n", "#", "Jamoa"));
                message.append("─".repeat(25)).append("\n");
                
                for (TeamStanding standing : standings) {
                    String medal = switch (standing.getPosition()) {
                        case 1 -> "🥇";
                        case 2 -> "🥈";
                        case 3 -> "🥉";
                        default -> "  ";
                    };
                    message.append(String.format("%s %-3d %-20s\n",
                            medal,
                            standing.getPosition(),
                            truncate(standing.getTeamName(), 20)));
                }
                message.append("```\n");
            } else {
                // League table display
                message.append("```\n");
                message.append(String.format("%-3s %-20s %2s %2s %2s %2s %3s\n", 
                        "#", "Jamoa", "O", "G", "D", "M", "O"));
                message.append("─".repeat(45)).append("\n");

                int position = 1;
                for (TeamStanding standing : standings) {
                    message.append(String.format("%-3d %-20s %2d %2d %2d %2d %3d\n",
                            position++,
                            truncate(standing.getTeamName(), 20),
                            standing.getPlayed(),
                            standing.getWon(),
                            standing.getDrawn(),
                            standing.getLost(),
                            standing.getPoints()));
                }
                
                message.append("```\n\n");
                message.append("O'yin - O'ynalgan, G - G'alaba, D - Durang, M - Mag'lubiyat, O - Ochko");
            }

            // Create back button keyboard
            InlineKeyboardMarkup keyboard = createBackKeyboard(tournamentId);

            if (update.hasCallbackQuery()) {
                bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(), message.toString(), keyboard);
            } else {
                bot.sendMessage(chatId, message.toString(), keyboard);
            }

        } catch (Exception e) {
            log.error("Error showing standings", e);
            bot.sendMessage(chatId, "Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
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

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private InlineKeyboardMarkup createBackKeyboard(Long tournamentId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

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
        return "/standings";
    }
}
