package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartTournamentCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();

        // Check if user is admin or organizer
        Optional<User> userOpt = userService.getUserByTelegramId(userId);
        if (userOpt.isEmpty() || (userOpt.get().getRole() != Role.ADMIN && 
                                   userOpt.get().getRole() != Role.MODERATOR && 
                                   userOpt.get().getRole() != Role.ORGANIZER)) {
            bot.sendMessage(chatId, "❌ Faqat adminlar va tashkilotchilar turnirni boshlashi mumkin.");
            return;
        }

        // Extract tournament ID from command
        String[] parts = messageText.split(" ");
        if (parts.length < 2) {
            bot.sendMessage(chatId, "❌ Turnir ID ni kiriting: /starttournament <id>");
            return;
        }

        try {
            Long tournamentId = Long.parseLong(parts[1]);
            User user = userOpt.get();
            
            // Check ownership if not admin
            if (user.getRole() == Role.ORGANIZER) {
                if (!tournamentService.isOwner(tournamentId, user)) {
                    bot.sendMessage(chatId, "❌ Siz faqat o'zingiz yaratgan turnirlarni boshlashingiz mumkin.");
                    return;
                }
            }
            
            tournamentService.startTournament(tournamentId);
            
            bot.sendMessage(chatId, 
                    "✅ Turnir muvaffaqiyatli boshlandi!\n\n" +
                    "O'yinlar yaratildi va ishtirokchilarga xabar beriladi.");
            
            log.info("Tournament {} started by user {}", tournamentId, userId);
            
        } catch (NumberFormatException e) {
            bot.sendMessage(chatId, "❌ Noto'g'ri turnir ID formati.");
        } catch (IllegalStateException e) {
            bot.sendMessage(chatId, "❌ " + e.getMessage());
        } catch (Exception e) {
            log.error("Error starting tournament", e);
            bot.sendMessage(chatId, "Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    @Override
    public String getCommandName() {
        return "/starttournament";
    }
}
