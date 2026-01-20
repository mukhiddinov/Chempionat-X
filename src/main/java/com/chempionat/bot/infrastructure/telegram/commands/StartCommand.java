package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.event.UserStartedEvent;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.TeamRepository;
import com.chempionat.bot.domain.repository.TournamentRepository;
import com.chempionat.bot.infrastructure.telegram.KeyboardFactory;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartCommand implements TelegramCommand {

    private final UserService userService;
    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.getMessage().getChatId();
        org.telegram.telegrambots.meta.api.objects.User telegramUser = update.getMessage().getFrom();
        String messageText = update.getMessage().getText();

        try {
            User user = userService.getOrCreateUser(
                    telegramUser.getId(),
                    telegramUser.getUserName(),
                    telegramUser.getFirstName(),
                    telegramUser.getLastName()
            );

            // Check for deep link parameter (e.g., /start join_123)
            if (messageText != null && messageText.contains(" ")) {
                String[] parts = messageText.split(" ");
                if (parts.length > 1 && parts[1].startsWith("join_")) {
                    handleJoinTournamentDeepLink(bot, chatId, user, parts[1]);
                    return;
                }
            }

            String welcomeMessage = String.format(
                    "Xush kelibsiz, %s! 🎮⚽\n\n" +
                    "Chempionat-X - Futbol turnirlarini boshqarish boti.\n\n" +
                    "Quyidagi funksiyalardan foydalanishingiz mumkin.",
                    user.getFirstName() != null ? user.getFirstName() : "O'yinchi"
            );

            // Create dynamic keyboard based on user role and tournament participation
            ReplyKeyboardMarkup keyboard = createDynamicKeyboard(user);

            bot.sendMessage(chatId, welcomeMessage, keyboard);

            // Publish event for notification
            eventPublisher.publishEvent(new UserStartedEvent(this, user));
            
            log.info("User started: telegramId={}, username={}, role={}", 
                    telegramUser.getId(), telegramUser.getUserName(), user.getRole());
        } catch (Exception e) {
            log.error("Error executing start command", e);
            bot.sendMessage(chatId, "Kechirasiz, xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        }
    }

    /**
     * Create keyboard based on user role and participation
     */
    private ReplyKeyboardMarkup createDynamicKeyboard(User user) {
        Role role = user.getRole();
        
        // ADMIN gets admin menu
        if (role == Role.ADMIN) {
            return KeyboardFactory.createAdminMenu();
        }
        
        // ORGANIZER gets organizer menu
        if (role == Role.ORGANIZER) {
            return KeyboardFactory.createOrganizerMenu();
        }
        
        // USER gets menu based on tournament participation
        List<Tournament> joinedTournaments = tournamentRepository.findTournamentsByPlayerId(user.getId());
        
        if (joinedTournaments.isEmpty()) {
            // No tournaments joined - simple menu
            return KeyboardFactory.createUserMenuWithoutTournaments();
        } else {
            // Has tournaments - show My Tournaments button
            return KeyboardFactory.createUserMenuWithTournaments();
        }
    }

    private void handleJoinTournamentDeepLink(TelegramBot bot, Long chatId, User user, String parameter) {
        try {
            // Extract tournament ID from parameter (format: join_123)
            String[] parts = parameter.split("_");
            if (parts.length < 2) {
                bot.sendMessage(chatId, "❌ Noto'g'ri havola formati");
                return;
            }

            Long tournamentId = Long.parseLong(parts[1]);
            Tournament tournament = tournamentRepository.findById(tournamentId).orElse(null);

            if (tournament == null) {
                bot.sendMessage(chatId, "❌ Turnir topilmadi");
                return;
            }

            // Get team count
            long teamsCount = teamRepository.findByTournament(tournament).size();
            
            // Show tournament info with Join button
            String message = String.format(
                    "🏆 *%s*\n\n" +
                    "📝 Turi: %s\n" +
                    "👥 Ishtirokchilar: %d",
                    tournament.getName(),
                    tournament.getType().name().equals("LEAGUE") ? "Liga" : "Pley-off",
                    teamsCount
            );
            
            if (tournament.getMaxParticipants() != null) {
                message += "/" + tournament.getMaxParticipants();
            }
            
            message += "\n📅 Status: " + (tournament.getIsActive() ? "Faol" : "Faol emas");
            message += "\n\nTurnirga qo'shilish uchun quyidagi tugmani bosing:";

            // Create inline keyboard with Join button
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            
            InlineKeyboardButton joinButton = new InlineKeyboardButton("✅ Qo'shilish");
            joinButton.setCallbackData("jointournament:" + tournamentId);
            
            row.add(joinButton);
            rows.add(row);
            keyboard.setKeyboard(rows);

            bot.sendMessage(chatId, message, keyboard);
            
            log.info("User {} opened join link for tournament {}", user.getId(), tournamentId);

        } catch (NumberFormatException e) {
            log.error("Invalid tournament ID in deep link", e);
            bot.sendMessage(chatId, "❌ Noto'g'ri turnir identifikatori");
        } catch (Exception e) {
            log.error("Error handling join tournament deep link", e);
            bot.sendMessage(chatId, "❌ Xatolik yuz berdi");
        }
    }

    @Override
    public String getCommandName() {
        return "/start";
    }
}
