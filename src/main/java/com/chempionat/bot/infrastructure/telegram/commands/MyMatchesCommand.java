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

            // Build message with HTML formatting for clickable user links
            StringBuilder message = new StringBuilder();
            message.append("⚽ <b>Mening o'yinlarim</b>\n");
            message.append("🏆 ").append(escapeHtml(tournament.getName())).append("\n\n");

            // Group matches by round (maintain insertion order)
            Map<Integer, List<Match>> matchesByRound = matches.stream()
                .collect(Collectors.groupingBy(Match::getRound, LinkedHashMap::new, Collectors.toList()));

            for (Map.Entry<Integer, List<Match>> entry : matchesByRound.entrySet()) {
                Integer round = entry.getKey();
                List<Match> roundMatches = entry.getValue();

                message.append("📅 <b>Round ").append(round).append("</b>\n");

                for (Match match : roundMatches) {
                    boolean isHome = match.getHomeTeam().getId().equals(userTeam.getId());
                    
                    // Format user links - clickable to Telegram profile
                    String homeUserLink = formatHomeUserLink(match.getHomeTeam().getUser());
                    String awayUserLink = formatAwayUserLink(match.getAwayTeam().getUser());

                    message.append("   ");

                    // Check if result exists in DB (regardless of state)
                    boolean hasResult = match.getHomeScore() != null && match.getAwayScore() != null;
                    
                    if (hasResult) {
                        // Show with score
                        if (isHome) {
                            message.append("🏠 ").append(homeUserLink).append(" ")
                                   .append("<b>").append(match.getHomeScore()).append(":")
                                   .append(match.getAwayScore()).append("</b> ")
                                   .append(awayUserLink);
                        } else {
                            message.append(homeUserLink).append(" ")
                                   .append("<b>").append(match.getHomeScore()).append(":")
                                   .append(match.getAwayScore()).append("</b> ")
                                   .append(awayUserLink).append(" ✈️");
                        }
                        // Add status indicator
                        if (match.getState() == MatchLifecycleState.APPROVED) {
                            message.append(" ✅");
                        } else if (match.getState() == MatchLifecycleState.PENDING_APPROVAL) {
                            message.append(" 🕐");
                        }
                    } else {
                        // No result yet - show waiting
                        if (isHome) {
                            message.append("🏠 ").append(homeUserLink).append(" ⏳ ").append(awayUserLink);
                        } else {
                            message.append(homeUserLink).append(" ⏳ ").append(awayUserLink).append(" ✈️");
                        }
                    }
                    message.append("\n");
                }
                message.append("\n");
            }

            // Create inline keyboard with "Natija yuklash" button
            InlineKeyboardMarkup keyboard = createMatchActionKeyboard(userTeam, tournament);

            bot.editMessageHtml(chatId, messageId, message.toString(), keyboard);

            log.info("User {} viewed their matches for tournament {}", userId, tournamentId);

        } catch (Exception e) {
            log.error("Error showing user matches", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    /**
     * Format user as clickable link for Telegram (for home players).
     * - If username exists: @username linking to t.me/username
     * - If no username: firstname (no @) linking to tg://user?id=telegramId
     */
    private String formatHomeUserLink(User user) {
        return formatUserLink(user, false);
    }

    /**
     * Format user as clickable link for Telegram (for away players).
     * - If username exists: @username linking to t.me/username
     * - If no username: @firstname linking to tg://user?id=telegramId
     */
    private String formatAwayUserLink(User user) {
        return formatUserLink(user, true);
    }

    /**
     * Format user as clickable link for Telegram.
     * @param user the user to format
     * @param addAtForFirstname if true, prepend @ to firstname when no username
     */
    private String formatUserLink(User user, boolean addAtForFirstname) {
        if (user == null) {
            return "Unknown";
        }
        
        String username = user.getUsername();
        Long telegramId = user.getTelegramId();
        String firstName = user.getFirstName() != null ? user.getFirstName() : "User";
        
        if (username != null && !username.isEmpty()) {
            // Has username - link to t.me/username
            return "<a href=\"https://t.me/" + escapeHtml(username) + "\">@" + escapeHtml(username) + "</a>";
        } else if (telegramId != null) {
            // No username - link firstname to tg://user?id=
            String displayName = addAtForFirstname ? "@" + escapeHtml(firstName) : escapeHtml(firstName);
            return "<a href=\"tg://user?id=" + telegramId + "\">" + displayName + "</a>";
        } else {
            // Fallback - just show firstname
            String displayName = addAtForFirstname ? "@" + escapeHtml(firstName) : escapeHtml(firstName);
            return displayName;
        }
    }

    /**
     * Escape HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
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
