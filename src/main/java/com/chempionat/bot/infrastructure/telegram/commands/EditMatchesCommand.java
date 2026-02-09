package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import com.chempionat.bot.infrastructure.telegram.UserContext;
import com.chempionat.bot.infrastructure.telegram.util.PaginationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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
public class EditMatchesCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final MatchRepository matchRepository;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = extractChatId(update);
        Long userId = extractUserId(update);
        UserContext context = UserContext.get(userId);

        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextInput(update, bot, context);
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            
            if (callbackData.startsWith("edit_matches:")) {
                showRoundsList(update, bot);
            } else if (callbackData.startsWith("select_round:")) {
                showMatchesList(update, bot);
            } else if (callbackData.startsWith("edit_match:")) {
                showMatchEditOptions(update, bot);
            } else if (callbackData.startsWith("edit_home_score:")) {
                startHomeScoreEdit(update, bot, context);
            } else if (callbackData.startsWith("edit_away_score:")) {
                startAwayScoreEdit(update, bot, context);
            } else if (callbackData.startsWith("page:matches_round_")) {
                handleMatchesPagination(update, bot);
            } else if (callbackData.equals("back_to_rounds")) {
                backToRounds(update, bot, context);
            } else if (callbackData.startsWith("back_to_matches:")) {
                backToMatches(update, bot, context);
            }
        }
    }

    private void showRoundsList(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long tournamentId = Long.parseLong(callbackData.split(":")[1]);

        try {
            Tournament tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            List<Match> matches = matchRepository.findByTournament(tournament);
            
            if (matches.isEmpty()) {
                bot.editMessage(chatId, messageId, "❌ Bu turnirda hali o'yinlar yo'q");
                return;
            }

            // Get unique rounds
            List<Integer> rounds = matches.stream()
                    .map(Match::getRound)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            String message = "✏️ O'yinlarni tahrirlash\n\n" +
                    "🏆 " + tournament.getName() + "\n\n" +
                    "Turni tanlang:";

            InlineKeyboardMarkup keyboard = createRoundsKeyboard(rounds, tournamentId);
            bot.editMessage(chatId, messageId, message, keyboard);

        } catch (Exception e) {
            log.error("Error showing rounds list", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    private InlineKeyboardMarkup createRoundsKeyboard(List<Integer> rounds, Long tournamentId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Integer round : rounds) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("📍 Tur " + round);
            button.setCallbackData("select_round:" + tournamentId + ":" + round);
            row.add(button);
            rows.add(row);
        }

        // Back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("⬅️ Ortga");
        backBtn.setCallbackData("manage_tournament:" + tournamentId);
        backRow.add(backBtn);
        rows.add(backRow);

        markup.setKeyboard(rows);
        return markup;
    }

    private void showMatchesList(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        
        String[] parts = callbackData.split(":");
        Long tournamentId = Long.parseLong(parts[1]);
        Integer round = Integer.parseInt(parts[2]);

        try {
            Tournament tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            List<Match> matches = matchRepository.findByTournamentAndRound(tournament, round);
            
            if (matches.isEmpty()) {
                bot.editMessage(chatId, messageId, "❌ Bu turda o'yinlar yo'q");
                return;
            }

            String message = buildMatchesListMessage(tournament, round, matches.size());
            InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboardWithBack(
                    matches,
                    0,
                    10,
                    this::formatMatchButton,
                    m -> "edit_match:" + m.getId(),
                    "matches_round_" + round,
                    "⬅️ Ortga",
                    "back_to_rounds"
            );

            bot.editMessage(chatId, messageId, message, keyboard);

        } catch (Exception e) {
            log.error("Error showing matches list", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    private String buildMatchesListMessage(Tournament tournament, Integer round, int matchesCount) {
        return "✏️ O'yinlarni tahrirlash\n\n" +
                "🏆 " + tournament.getName() + "\n" +
                "📍 Tur " + round + "\n" +
                "⚽ O'yinlar: " + matchesCount + "\n\n" +
                "Tahrirlamoqchi bo'lgan o'yinni tanlang:";
    }

    private String formatMatchButton(Match match) {
        if (match.getIsBye() != null && match.getIsBye()) {
            String username = match.getHomeTeam().getUser().getUsername();
            return "🛌 " + (username != null ? "@" + username : match.getHomeTeam().getUser().getFirstName());
        }

        String homeUsername = match.getHomeTeam().getUser().getUsername();
        String awayUsername = match.getAwayTeam().getUser().getUsername();
        
        String home = homeUsername != null ? "@" + homeUsername : match.getHomeTeam().getUser().getFirstName();
        String away = awayUsername != null ? "@" + awayUsername : match.getAwayTeam().getUser().getFirstName();
        
        Integer homeScore = match.getHomeScore() != null ? match.getHomeScore() : 0;
        Integer awayScore = match.getAwayScore() != null ? match.getAwayScore() : 0;
        
        // Truncate long names
        if (home.length() > 15) home = home.substring(0, 12) + "...";
        if (away.length() > 15) away = away.substring(0, 12) + "...";
        
        return home + " " + homeScore + ":" + awayScore + " " + away;
    }

    private void showMatchEditOptions(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long matchId = Long.parseLong(callbackData.split(":")[1]);

        try {
            Match match = matchRepository.findById(matchId)
                    .orElseThrow(() -> new IllegalArgumentException("Match not found"));

            if (match.getIsBye() != null && match.getIsBye()) {
                bot.editMessage(chatId, messageId, "❌ Dam olish kunini tahrirlash mumkin emas");
                return;
            }

            String message = buildMatchEditMessage(match);
            InlineKeyboardMarkup keyboard = createMatchEditKeyboard(match);

            bot.editMessage(chatId, messageId, message, keyboard);

        } catch (Exception e) {
            log.error("Error showing match edit options", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    private String buildMatchEditMessage(Match match) {
        String homeUsername = match.getHomeTeam().getUser().getUsername();
        String awayUsername = match.getAwayTeam().getUser().getUsername();
        
        String home = homeUsername != null ? "@" + homeUsername : match.getHomeTeam().getUser().getFirstName();
        String away = awayUsername != null ? "@" + awayUsername : match.getAwayTeam().getUser().getFirstName();
        
        Integer homeScore = match.getHomeScore() != null ? match.getHomeScore() : 0;
        Integer awayScore = match.getAwayScore() != null ? match.getAwayScore() : 0;

        return "✏️ O'yin natijasini tahrirlash\n\n" +
                "📍 Tur " + match.getRound() + "\n\n" +
                "🏠 " + home + "\n" +
                "🔢 Hisob: " + homeScore + " : " + awayScore + "\n" +
                "✈️ " + away + "\n\n" +
                "Tahrirlamoqchi bo'lgan qismni tanlang:";
    }

    private InlineKeyboardMarkup createMatchEditKeyboard(Match match) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        Integer homeScore = match.getHomeScore() != null ? match.getHomeScore() : 0;
        Integer awayScore = match.getAwayScore() != null ? match.getAwayScore() : 0;

        // Home score edit
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton homeBtn = new InlineKeyboardButton();
        homeBtn.setText("🏠 Uy: " + homeScore);
        homeBtn.setCallbackData("edit_home_score:" + match.getId());
        row1.add(homeBtn);
        rows.add(row1);

        // Away score edit
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton awayBtn = new InlineKeyboardButton();
        awayBtn.setText("✈️ Mehmon: " + awayScore);
        awayBtn.setCallbackData("edit_away_score:" + match.getId());
        row2.add(awayBtn);
        rows.add(row2);

        // Back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("⬅️ Ortga");
        backBtn.setCallbackData("back_to_matches:" + match.getTournament().getId() + ":" + match.getRound());
        backRow.add(backBtn);
        rows.add(backRow);

        markup.setKeyboard(rows);
        return markup;
    }

    private void startHomeScoreEdit(Update update, TelegramBot bot, UserContext context) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long matchId = Long.parseLong(callbackData.split(":")[1]);

        context.setCurrentCommand(getCommandName());
        context.setData("editing_match_id", matchId);
        context.setData("editing_field", "home_score");

        bot.editMessage(chatId, messageId, 
                "🏠 Uy egasi hisobini kiriting (0-99):\n\n" +
                "Yoki /cancel bekor qilish uchun");
    }

    private void startAwayScoreEdit(Update update, TelegramBot bot, UserContext context) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long matchId = Long.parseLong(callbackData.split(":")[1]);

        context.setCurrentCommand(getCommandName());
        context.setData("editing_match_id", matchId);
        context.setData("editing_field", "away_score");

        bot.editMessage(chatId, messageId, 
                "✈️ Mehmon hisobini kiriting (0-99):\n\n" +
                "Yoki /cancel bekor qilish uchun");
    }

    private void handleTextInput(Update update, TelegramBot bot, UserContext context) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        if (text.equals("/cancel")) {
            context.clearData();
            bot.sendMessage(chatId, "❌ Bekor qilindi");
            return;
        }

        Long matchId = context.getDataAsLong("editing_match_id");
        String field = context.getDataAsString("editing_field");

        if (matchId == null || field == null) {
            bot.sendMessage(chatId, "❌ Xatolik yuz berdi");
            context.clearData();
            return;
        }

        try {
            int score = Integer.parseInt(text);
            
            if (score < 0 || score > 99) {
                bot.sendMessage(chatId, "❌ Hisob 0 va 99 orasida bo'lishi kerak. Qaytadan kiriting:");
                return;
            }

            Match match = matchRepository.findById(matchId)
                    .orElseThrow(() -> new IllegalArgumentException("Match not found"));

            if (field.equals("home_score")) {
                match.setHomeScore(score);
            } else {
                match.setAwayScore(score);
            }

            matchRepository.save(match);

            String message = "✅ Hisob yangilandi!\n\n" +
                    buildMatchSummary(match);

            bot.sendMessage(chatId, message);
            
            log.info("Match {} score updated: {}:{} by user {}", 
                     matchId, match.getHomeScore(), match.getAwayScore(), update.getMessage().getFrom().getId());

            context.clearData();

        } catch (NumberFormatException e) {
            bot.sendMessage(chatId, "❌ Iltimos, faqat raqam kiriting:");
        } catch (Exception e) {
            log.error("Error updating match score", e);
            bot.sendMessage(chatId, "❌ Xatolik yuz berdi");
            context.clearData();
        }
    }

    private String buildMatchSummary(Match match) {
        String homeUsername = match.getHomeTeam().getUser().getUsername();
        String awayUsername = match.getAwayTeam().getUser().getUsername();
        
        String home = homeUsername != null ? "@" + homeUsername : match.getHomeTeam().getUser().getFirstName();
        String away = awayUsername != null ? "@" + awayUsername : match.getAwayTeam().getUser().getFirstName();

        return "📍 Tur " + match.getRound() + "\n" +
               "🏠 " + home + "\n" +
               "🔢 " + match.getHomeScore() + " : " + match.getAwayScore() + "\n" +
               "✈️ " + away;
    }

    private void handleMatchesPagination(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();

        try {
            // Extract round and page from callback: page:matches_round_1:2
            String[] parts = callbackData.split(":");
            String roundPart = parts[1].replace("matches_round_", "");
            Integer round = Integer.parseInt(roundPart);
            Integer page = Integer.parseInt(parts[2]);

            // We need tournament ID - get it from first match
            List<Match> allMatches = matchRepository.findByRound(round);
            if (allMatches.isEmpty()) {
                bot.editMessage(chatId, messageId, "❌ O'yinlar topilmadi");
                return;
            }

            Tournament tournament = allMatches.get(0).getTournament();
            List<Match> matches = matchRepository.findByTournamentAndRound(tournament, round);

            String message = buildMatchesListMessage(tournament, round, matches.size());
            InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboardWithBack(
                    matches,
                    page,
                    10,
                    this::formatMatchButton,
                    m -> "edit_match:" + m.getId(),
                    "matches_round_" + round,
                    "⬅️ Ortga",
                    "back_to_rounds"
            );

            bot.editMessage(chatId, messageId, message, keyboard);

        } catch (Exception e) {
            log.error("Error handling matches pagination", e);
        }
    }

    private void backToRounds(Update update, TelegramBot bot, UserContext context) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        // Get tournament ID from context or from any match
        Long tournamentId = context.getDataAsLong("tournament_id");
        
        if (tournamentId == null) {
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
            return;
        }

        // Simulate the edit_matches callback
        Update simulatedUpdate = new Update();
        // This is a simplified approach - in production, reconstruct properly
        showRoundsList(update, bot);
    }

    private void backToMatches(Update update, TelegramBot bot, UserContext context) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        
        String[] parts = callbackData.split(":");
        Long tournamentId = Long.parseLong(parts[1]);
        Integer round = Integer.parseInt(parts[2]);

        try {
            Tournament tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            List<Match> matches = matchRepository.findByTournamentAndRound(tournament, round);

            String message = buildMatchesListMessage(tournament, round, matches.size());
            InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboardWithBack(
                    matches,
                    0,
                    10,
                    this::formatMatchButton,
                    m -> "edit_match:" + m.getId(),
                    "matches_round_" + round,
                    "⬅️ Ortga",
                    "back_to_rounds"
            );

            bot.editMessage(chatId, messageId, message, keyboard);

        } catch (Exception e) {
            log.error("Error going back to matches", e);
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
        return "/editmatches";
    }
}
