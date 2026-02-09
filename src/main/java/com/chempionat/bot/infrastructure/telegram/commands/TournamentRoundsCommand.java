package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentRoundsCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final MatchRepository matchRepository;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = extractChatId(update);
        Long tournamentId = extractTournamentId(update);

        if (tournamentId == null) {
            bot.sendMessage(chatId, "❌ Noto'g'ri turnir identifikatori");
            return;
        }

        try {
            Tournament tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));
            List<Match> matches = matchRepository.findByTournament(tournament);

            if (matches.isEmpty()) {
                bot.sendMessage(chatId, "📭 Hozircha o'yinlar yo'q");
                return;
            }

            String message = buildRoundsMessage(tournament, matches);
            InlineKeyboardMarkup keyboard = createBackKeyboard(tournamentId);
            
            bot.sendMessage(chatId, message, keyboard);
            
        } catch (Exception e) {
            log.error("Error showing tournament rounds for tournament {}", tournamentId, e);
            bot.sendMessage(chatId, "❌ Turnir topilmadi");
        }
    }

    private String buildRoundsMessage(Tournament tournament, List<Match> matches) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("📅 ").append(tournament.getName()).append(" - Turlar\n\n");
        
        // Group matches by round
        Map<Integer, List<Match>> matchesByRound = matches.stream()
            .collect(Collectors.groupingBy(Match::getRound));
        
        int maxRound = matches.stream()
            .mapToInt(Match::getRound)
            .max()
            .orElse(0);
        
        for (int round = 1; round <= maxRound; round++) {
            sb.append("━━━━━━━━━━━━━━━━━\n");
            sb.append("📍 Tur ").append(round).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━\n\n");
            
            List<Match> roundMatches = matchesByRound.get(round);
            
            if (roundMatches == null || roundMatches.isEmpty()) {
                sb.append("  Ushbu turda o'yinlar yo'q\n\n");
                continue;
            }
            
            for (Match match : roundMatches) {
                if (match.getIsBye() != null && match.getIsBye()) {
                    // Show as rest round
                    String username = match.getHomeTeam().getUser().getUsername();
                    if (username != null) {
                        sb.append("  🛌 @").append(username).append(" - Dam olish kuni\n");
                    } else {
                        sb.append("  🛌 ").append(match.getHomeTeam().getUser().getFirstName())
                          .append(" - Dam olish kuni\n");
                    }
                } else {
                    // Regular match
                    String homeUsername = match.getHomeTeam().getUser().getUsername();
                    String awayUsername = match.getAwayTeam().getUser().getUsername();
                    
                    sb.append("  ⚽ ");
                    
                    if (homeUsername != null) {
                        sb.append("@").append(homeUsername);
                    } else {
                        sb.append(match.getHomeTeam().getUser().getFirstName());
                    }
                    
                    // Show score if match is completed
                    if (match.getHomeScore() != null && match.getAwayScore() != null) {
                        sb.append(" [").append(match.getHomeScore())
                          .append(":").append(match.getAwayScore()).append("] ");
                    } else {
                        sb.append(" vs ");
                    }
                    
                    if (awayUsername != null) {
                        sb.append("@").append(awayUsername);
                    } else {
                        sb.append(match.getAwayTeam().getUser().getFirstName());
                    }
                    
                    // Status indicator
                    if (match.getHomeScore() != null && match.getAwayScore() != null) {
                        if (match.getResult() != null && match.getResult().getIsApproved() != null && match.getResult().getIsApproved()) {
                            sb.append(" ✅");
                        } else {
                            sb.append(" ⏳");
                        }
                    }
                    
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        
        // Legend
        sb.append("━━━━━━━━━━━━━━━━━\n");
        sb.append("✅ - Tasdiqlangan\n");
        sb.append("⏳ - Tasdiqlanmagan\n");
        sb.append("🛌 - Dam olish kuni\n");
        
        return sb.toString();
    }

    private InlineKeyboardMarkup createBackKeyboard(Long tournamentId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Ortga");
        backButton.setCallbackData("view_tournament:" + tournamentId);
        row.add(backButton);
        
        rows.add(row);
        markup.setKeyboard(rows);
        
        return markup;
    }

    private Long extractTournamentId(Update update) {
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            // Format: rounds:123
            if (data.startsWith("rounds:")) {
                try {
                    return Long.parseLong(data.substring("rounds:".length()));
                } catch (NumberFormatException e) {
                    log.error("Invalid tournament ID in callback: {}", data, e);
                }
            }
        }
        return null;
    }

    private Long extractChatId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        } else if (update.hasCallbackQuery()) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }

    @Override
    public String getCommandName() {
        return "/rounds";
    }
}
