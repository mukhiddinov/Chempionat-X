package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.application.service.UserService;
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

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentsCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final UserService userService;
    private final TeamRepository teamRepository;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId;
        Long userId;
        Integer messageId = null;

        if (update.hasMessage()) {
            chatId = update.getMessage().getChatId();
            userId = update.getMessage().getFrom().getId();
        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            userId = update.getCallbackQuery().getFrom().getId();
            messageId = update.getCallbackQuery().getMessage().getMessageId();
            
            String callbackData = update.getCallbackQuery().getData();
            
            // Handle specific tournament view
            if (callbackData.startsWith("tournament:")) {
                Long tournamentId = Long.parseLong(callbackData.split(":")[1]);
                showTournamentDetails(bot, chatId, messageId, userId, tournamentId);
                return;
            }
        } else {
            return;
        }

        try {
            List<Tournament> tournaments = tournamentService.getAllTournaments();

            if (tournaments.isEmpty()) {
                String message = "🏆 Hozircha turnirlar yo'q.";
                
                if (messageId != null) {
                    bot.editMessage(chatId, messageId, message);
                } else {
                    bot.sendMessage(chatId, message);
                }
                return;
            }

            StringBuilder message = new StringBuilder("🏆 Barcha turnirlar:\n\n");

            for (Tournament tournament : tournaments) {
                message.append("📌 ").append(tournament.getName()).append("\n");
                message.append("   Format: ").append(getTypeText(tournament.getType().name())).append("\n");
                
                int teamCount = teamRepository.findByTournament(tournament).size();
                message.append("   Ishtirokchilar: ").append(teamCount).append("\n");
                
                if (tournament.getStartDate() != null) {
                    message.append("   Boshlangan: ")
                           .append(tournament.getStartDate().format(DATE_FORMATTER))
                           .append("\n");
                }
                message.append("\n");
            }

            message.append("Turnirni ko'rish uchun quyidagi tugmalardan birini tanlang:");

            List<KeyboardFactory.TournamentButton> buttons = tournaments.stream()
                    .map(t -> new KeyboardFactory.TournamentButton(t.getId(), t.getName()))
                    .collect(Collectors.toList());

            if (messageId != null) {
                bot.editMessage(chatId, messageId, message.toString(), 
                               KeyboardFactory.createTournamentListKeyboard(buttons));
            } else {
                bot.sendMessage(chatId, message.toString(), 
                               KeyboardFactory.createTournamentListKeyboard(buttons));
            }

        } catch (Exception e) {
            log.error("Error showing tournaments", e);
            bot.sendMessage(chatId, "Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    private void showTournamentDetails(TelegramBot bot, Long chatId, Integer messageId, 
                                      Long userId, Long tournamentId) {
        try {
            Optional<Tournament> tournamentOpt = tournamentService.getTournamentById(tournamentId);
            if (tournamentOpt.isEmpty()) {
                bot.editMessage(chatId, messageId, "Turnir topilmadi.");
                return;
            }

            Tournament tournament = tournamentOpt.get();
            User user = userService.getUserByTelegramId(userId).orElse(null);
            
            boolean isParticipant = false;
            if (user != null) {
                isParticipant = teamRepository.findByTournamentAndUser(tournament, user).isPresent();
            }

            List<Team> teams = teamRepository.findByTournament(tournament);

            StringBuilder message = new StringBuilder();
            message.append("🏆 ").append(tournament.getName()).append("\n\n");
            
            if (tournament.getDescription() != null && !tournament.getDescription().isEmpty()) {
                message.append("📝 ").append(tournament.getDescription()).append("\n\n");
            }
            
            message.append("📊 Format: ").append(getTypeText(tournament.getType().name())).append("\n");
            message.append("👥 Ishtirokchilar: ").append(teams.size()).append("\n");
            
            if (tournament.getStartDate() != null) {
                message.append("📅 Boshlangan: ")
                       .append(tournament.getStartDate().format(DATE_FORMATTER))
                       .append("\n");
            }
            
            if (isParticipant) {
                message.append("\n✅ Siz bu turnirda ishtirok etyapsiz\n");
            }

            bot.editMessage(chatId, messageId, message.toString(),
                           KeyboardFactory.createTournamentActionsKeyboard(tournamentId, isParticipant));

        } catch (Exception e) {
            log.error("Error showing tournament details", e);
            bot.editMessage(chatId, messageId, "Xatolik yuz berdi.");
        }
    }

    private String getTypeText(String type) {
        return switch (type) {
            case "LEAGUE" -> "🏆 Liga (Round-robin)";
            case "PLAYOFF" -> "🥇 Olimpiya tizimi (Play-off)";
            default -> type;
        };
    }

    @Override
    public String getCommandName() {
        return "/tournaments";
    }
}
