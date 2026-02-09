package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.TeamRepository;
import com.chempionat.bot.domain.repository.TournamentRepository;
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
public class MyMatchesCommand implements TelegramCommand {

    private final UserService userService;
    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (!update.hasCallbackQuery()) {
            return;
        }

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();

        try {
            // Extract tournament ID from callback (format: my_matches:tournamentId)
            Long tournamentId = Long.parseLong(callbackData.split(":")[1]);

            Optional<User> userOpt = userService.getUserByTelegramId(userId);
            if (userOpt.isEmpty()) {
                bot.editMessage(chatId, messageId, "❌ Foydalanuvchi topilmadi");
                return;
            }

            User user = userOpt.get();
            Tournament tournament = tournamentRepository.findById(tournamentId).orElse(null);
            if (tournament == null) {
                bot.editMessage(chatId, messageId, "❌ Turnir topilmadi");
                return;
            }

            // Get user's team in this tournament
            Optional<Team> teamOpt = teamRepository.findByTournamentAndUser(tournament, user);
            if (teamOpt.isEmpty()) {
                bot.editMessage(chatId, messageId, "❌ Siz bu turnirda ishtirok etmayapsiz");
                return;
            }

            Team userTeam = teamOpt.get();

            // Get all matches for this team (exclude bye/self matches)
            List<Match> matches = matchRepository.findByTournamentAndTeam(tournament, userTeam)
                    .stream()
                    .filter(m -> !Boolean.TRUE.equals(m.getIsBye()))
                    .filter(m -> !m.getHomeTeam().getId().equals(m.getAwayTeam().getId()))
                    .collect(Collectors.toList());
            
            if (matches.isEmpty()) {
                bot.editMessage(chatId, messageId, 
                    "📋 Sizning o'yinlaringiz yo'q\n\n" +
                    "🏆 Turnir: " + tournament.getName());
                return;
            }

            // Sort matches by round
            matches.sort(Comparator.comparing(Match::getRound));

            // Build message
            StringBuilder message = new StringBuilder();
            message.append("⚽ Mening o'yinlarim\n");
            message.append("🏆 ").append(tournament.getName()).append("\n\n");

            // Group matches by round
            Map<Integer, List<Match>> matchesByRound = matches.stream()
                .collect(Collectors.groupingBy(Match::getRound));

            for (Map.Entry<Integer, List<Match>> entry : matchesByRound.entrySet()) {
                Integer round = entry.getKey();
                List<Match> roundMatches = entry.getValue();

                message.append("📅 Round ").append(round).append("\n");

                for (Match match : roundMatches) {
                    String homeTeamName = match.getHomeTeam().getName();
                    String awayTeamName = match.getAwayTeam().getName();
                    String homeUser = match.getHomeTeam().getUser().getUsername();
                    String awayUser = match.getAwayTeam().getUser().getUsername();
                    
                    // Handle null usernames
                    if (homeUser == null || homeUser.isEmpty()) {
                        homeUser = match.getHomeTeam().getUser().getFirstName();
                    }
                    if (awayUser == null || awayUser.isEmpty()) {
                        awayUser = match.getAwayTeam().getUser().getFirstName();
                    }

                    message.append("   ");

                    // Check if played
                    if (match.getState() == MatchLifecycleState.APPROVED || 
                        match.getState() == MatchLifecycleState.PLAYED) {
                        // Show with score
                        message.append(homeTeamName).append(" ")
                               .append(match.getHomeScore()).append(":")
                               .append(match.getAwayScore()).append(" ")
                               .append(awayTeamName);
                    } else {
                        // Show waiting emoji
                        message.append("@").append(homeUser)
                               .append(" ⏳ ")
                               .append("@").append(awayUser);
                    }
                    message.append("\n");
                }
                message.append("\n");
            }

            // Create inline keyboard with "Natija yuklash" button
            InlineKeyboardMarkup keyboard = createMatchActionKeyboard(userTeam, tournament);

            bot.editMessage(chatId, messageId, message.toString(), keyboard);

            log.info("User {} viewed their matches for tournament {}", userId, tournamentId);

        } catch (Exception e) {
            log.error("Error showing user matches", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    private InlineKeyboardMarkup createMatchActionKeyboard(Team userTeam, Tournament tournament) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Add "Natija yuklash" button
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        InlineKeyboardButton submitButton = new InlineKeyboardButton("📤 Natija yuklash");
        submitButton.setCallbackData("submit_result_user:" + userTeam.getUser().getId() + ":" + tournament.getId());
        actionRow.add(submitButton);
        rows.add(actionRow);

        // Add back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton("« Orqaga");
        backButton.setCallbackData("view_tournament:" + tournament.getId());
        backRow.add(backButton);
        rows.add(backRow);

        markup.setKeyboard(rows);
        return markup;
    }

    @Override
    public String getCommandName() {
        return "/mymatches";
    }
}
