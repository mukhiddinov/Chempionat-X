package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import com.chempionat.bot.infrastructure.telegram.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JoinTournamentCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId;
        Long userId;

        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            userId = update.getCallbackQuery().getFrom().getId();
            String callbackData = update.getCallbackQuery().getData();
            
            // Extract tournament ID from callback
            Long tournamentId = Long.parseLong(callbackData.split(":")[1]);
            
            UserContext context = UserContext.get(userId);
            context.setCurrentCommand(getCommandName());
            context.setData("tournamentId", tournamentId);
            
            bot.editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    "Jamoangiz nomini kiriting:");
            
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            userId = update.getMessage().getFrom().getId();
            
            UserContext context = UserContext.get(userId);
            Long tournamentId = (Long) context.getData("tournamentId");
            String teamName = update.getMessage().getText();
            
            if (tournamentId == null) {
                bot.sendMessage(chatId, "Xatolik yuz berdi. Iltimos qaytadan boshlang.");
                context.clearData();
                return;
            }
            
            try {
                Optional<User> userOpt = userService.getUserByTelegramId(userId);
                Optional<Tournament> tournamentOpt = tournamentService.getTournamentById(tournamentId);
                
                if (userOpt.isEmpty() || tournamentOpt.isEmpty()) {
                    bot.sendMessage(chatId, "Foydalanuvchi yoki turnir topilmadi.");
                    context.clearData();
                    return;
                }
                
                User user = userOpt.get();
                Tournament tournament = tournamentOpt.get();
                
                Team team = tournamentService.joinTournament(tournament, user, teamName);
                
                String message = String.format(
                        "✅ Siz \"%s\" turniriga muvaffaqiyatli qo'shildingiz!\n\n" +
                        "Jamoa nomi: %s\n\n" +
                        "Turnir boshlanganida sizga xabar beriladi.",
                        tournament.getName(),
                        teamName
                );
                
                bot.sendMessage(chatId, message);
                
                log.info("User {} joined tournament {} with team {}", 
                         userId, tournamentId, team.getId());
                
            } catch (IllegalStateException e) {
                bot.sendMessage(chatId, "❌ " + e.getMessage());
            } catch (Exception e) {
                log.error("Error joining tournament", e);
                bot.sendMessage(chatId, "Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
            } finally {
                context.clearData();
            }
        }
    }

    @Override
    public String getCommandName() {
        return "/jointournament";
    }
}
