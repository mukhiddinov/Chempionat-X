package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.TeamRepository;
import com.chempionat.bot.infrastructure.telegram.KeyboardFactory;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentDetailsCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final TeamRepository teamRepository;
    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = extractChatId(update);
        Long userId = update.hasCallbackQuery() ? 
            update.getCallbackQuery().getFrom().getId() : 
            update.getMessage().getFrom().getId();
        Long tournamentId = extractTournamentId(update);

        if (tournamentId == null) {
            bot.sendMessage(chatId, "❌ Noto'g'ri turnir identifikatori");
            return;
        }

        try {
            Tournament tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));
            String message = buildTournamentDetailsMessage(tournament);
            boolean isLeague = tournament.getType() == TournamentType.LEAGUE;
            
            // Check if user is participant
            Optional<User> userOpt = userService.getUserByTelegramId(userId);
            boolean isParticipant = false;
            if (userOpt.isPresent()) {
                isParticipant = teamRepository.findByTournamentAndUser(tournament, userOpt.get()).isPresent();
            }
            
            // Create keyboard with My Matches button if participant
            InlineKeyboardMarkup keyboard = isParticipant ? 
                createParticipantKeyboard(tournamentId, isLeague) :
                KeyboardFactory.createTournamentDetailsKeyboard(tournamentId, isLeague);
            
            if (update.hasCallbackQuery()) {
                bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(), message, keyboard);
            } else {
                bot.sendMessage(chatId, message, keyboard);
            }
            
        } catch (Exception e) {
            log.error("Error showing tournament details for tournament {}", tournamentId, e);
            bot.sendMessage(chatId, "❌ Turnir topilmadi");
        }
    }

    private InlineKeyboardMarkup createParticipantKeyboard(Long tournamentId, boolean isLeague) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // My Matches button for participants
        List<InlineKeyboardButton> myMatchesRow = new ArrayList<>();
        InlineKeyboardButton myMatchesButton = InlineKeyboardButton.builder()
                .text("⚽ Mening o'yinlarim")
                .callbackData("my_matches:" + tournamentId)
                .build();
        myMatchesRow.add(myMatchesButton);
        rows.add(myMatchesRow);

        if (isLeague) {
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton standingsButton = InlineKeyboardButton.builder()
                    .text("📊 Jadval")
                    .callbackData("standings:" + tournamentId)
                    .build();
            row1.add(standingsButton);
            rows.add(row1);
        }

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton roundsButton = InlineKeyboardButton.builder()
                .text("📅 Turlar")
                .callbackData("rounds:" + tournamentId)
                .build();
        row2.add(roundsButton);
        rows.add(row2);

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .text("⬅️ Ortga")
                .callbackData("my_tournaments")
                .build();
        backRow.add(backButton);
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private String buildTournamentDetailsMessage(Tournament tournament) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("🏆 ").append(tournament.getName()).append("\n\n");
        
        sb.append("📋 Ma'lumotlar:\n");
        sb.append("Turi: ").append(getTournamentTypeText(tournament.getType())).append("\n");
        sb.append("Status: ").append(tournament.getIsActive() ? "✅ Aktiv" : "⏸️ Faol emas").append("\n");
        
        if (tournament.getStartDate() != null) {
            sb.append("Boshlanish sanasi: ")
              .append(tournament.getStartDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
              .append("\n");
        }
        
        if (tournament.getEndDate() != null) {
            sb.append("Tugash sanasi: ")
              .append(tournament.getEndDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
              .append("\n");
        }
        
        // Participants info
        List<Team> teams = teamRepository.findByTournament(tournament);
        sb.append("\n👥 Ishtirokchilar: ").append(teams.size());
        
        if (tournament.getMaxParticipants() != null) {
            sb.append("/").append(tournament.getMaxParticipants());
        }
        sb.append("\n");
        
        // Show participants
        if (!teams.isEmpty()) {
            sb.append("\n📋 Ro'yxat:\n");
            int index = 1;
            for (Team team : teams) {
                String username = team.getUser().getUsername();
                if (username != null) {
                    sb.append(index++).append(". @").append(username).append("\n");
                } else {
                    sb.append(index++).append(". ").append(team.getUser().getFirstName()).append("\n");
                }
            }
        }
        
        // Tournament settings
        if (tournament.getNumberOfRounds() != null) {
            sb.append("\n🔄 Turlar soni: ").append(tournament.getNumberOfRounds()).append("\n");
        }
        
        if (tournament.getAutoStart() != null && tournament.getAutoStart()) {
            sb.append("⚡ Avtomatik boshlanish: Yoqilgan\n");
        }
        
        return sb.toString();
    }

    private String getTournamentTypeText(TournamentType type) {
        return switch (type) {
            case LEAGUE -> "Liga";
            case PLAYOFF -> "Play-off";
        };
    }

    private Long extractTournamentId(Update update) {
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            // Format: view_tournament:123
            if (data.startsWith("view_tournament:")) {
                try {
                    return Long.parseLong(data.substring("view_tournament:".length()));
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
        return "/view_tournament";
    }
}
