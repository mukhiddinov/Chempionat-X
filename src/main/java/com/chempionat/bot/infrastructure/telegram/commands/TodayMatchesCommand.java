package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.MatchService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TodayMatchesCommand implements TelegramCommand {

    private final UserService userService;
    private final MatchService matchService;

    @Override
    public String getCommandName() {
        return "/todaysmatches";
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();

        User user = userService.getUserByTelegramId(telegramId)
                .orElseThrow(() -> new IllegalStateException("User not found. Use /start first."));

        List<Match> todaysMatches = matchService.getTodaysMatchesForUser(user);

        if (todaysMatches.isEmpty()) {
            bot.sendMessage(chatId, "📅 You have no matches scheduled for today.");
            return;
        }

        StringBuilder message = new StringBuilder("📅 *Your Today's Matches*\n\n");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        for (Match match : todaysMatches) {
            boolean isHome = matchService.isHomePlayer(match, user);
            String opponentName = isHome ? 
                    match.getAwayTeam().getUser().getFullName() : 
                    match.getHomeTeam().getUser().getFullName();
            
            String role = isHome ? "(Home)" : "(Away)";
            String stage = match.getStage() != null ? match.getStage().name() : "Match";
            String time = match.getScheduledTime() != null ? 
                    match.getScheduledTime().format(timeFormatter) : "TBD";
            String status = match.getState().name();

            message.append(String.format("Match vs %s %s\n", opponentName, role));
            message.append(String.format("Stage: %s\n", stage));
            message.append(String.format("Time: %s\n", time));
            message.append(String.format("Status: %s\n", status));
            
            if (isHome && "CREATED".equals(status)) {
                message.append(String.format("\n/submitresult_%d - Submit result\n", match.getId()));
            }
            
            message.append("\n");
        }

        bot.sendMessage(chatId, message.toString());
        log.debug("Today's matches sent to user: {}", telegramId);
    }
}
