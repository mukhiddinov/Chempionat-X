package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.ActorContextService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.TournamentRepository;
import com.chempionat.bot.infrastructure.telegram.KeyboardFactory;
import com.chempionat.bot.infrastructure.telegram.TelegramBot;
import com.chempionat.bot.infrastructure.telegram.TelegramCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ManageOrganizerCommand implements TelegramCommand {

    private final UserService userService;
    private final TournamentRepository tournamentRepository;
    private final ActorContextService actorContextService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (!update.hasCallbackQuery()) {
            return;
        }

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        String callbackData = update.getCallbackQuery().getData();

        // Check if user is admin
        Optional<User> adminOpt = userService.getUserByTelegramId(userId);
        if (adminOpt.isEmpty() || adminOpt.get().getRole() != Role.ADMIN) {
            bot.editMessage(chatId, messageId, "❌ Faqat adminlar ushbu bo'limga kirishi mumkin");
            return;
        }

        if (callbackData.startsWith("manage_organizer:")) {
            showOrganizerManagement(update, bot);
        } else if (callbackData.startsWith("impersonate:")) {
            startImpersonation(update, bot);
        } else if (callbackData.equals("exit_impersonation")) {
            exitImpersonation(update, bot);
        } else if (callbackData.startsWith("view_organizer_tournaments:")) {
            viewOrganizerTournaments(update, bot);
        } else if (callbackData.equals("manage_impersonated_tournaments")) {
            manageImpersonatedTournaments(update, bot);
        }
    }

    private void showOrganizerManagement(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long organizerId = Long.parseLong(callbackData.split(":")[1]);

        try {
            User organizer = userService.getUserById(organizerId)
                    .orElseThrow(() -> new IllegalArgumentException("Organizer not found"));

            List<Tournament> tournaments = tournamentRepository.findByCreatedBy(organizer);

            String message = buildOrganizerInfoMessage(organizer, tournaments.size());
            InlineKeyboardMarkup keyboard = createOrganizerManagementKeyboard(organizerId, tournaments.size());

            bot.editMessage(chatId, messageId, message, keyboard);

        } catch (Exception e) {
            log.error("Error showing organizer management", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    private String buildOrganizerInfoMessage(User organizer, int tournamentsCount) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("👤 Tashkilotchi ma'lumotlari\n\n");
        
        if (organizer.getUsername() != null) {
            sb.append("Username: @").append(organizer.getUsername()).append("\n");
        }
        
        sb.append("Ism: ").append(organizer.getFirstName()).append("\n");
        
        if (organizer.getLastName() != null) {
            sb.append("Familiya: ").append(organizer.getLastName()).append("\n");
        }
        
        sb.append("\n📊 Statistika:\n");
        sb.append("🏆 Turnirlar: ").append(tournamentsCount).append("\n");
        
        sb.append("\nQuyidagi amallardan birini tanlang:");
        
        return sb.toString();
    }

    private InlineKeyboardMarkup createOrganizerManagementKeyboard(Long organizerId, int tournamentsCount) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Impersonate button
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton impersonateBtn = new InlineKeyboardButton();
        impersonateBtn.setText("🎭 Tashkilotchi sifatida kirish");
        impersonateBtn.setCallbackData("impersonate:" + organizerId);
        row1.add(impersonateBtn);
        rows.add(row1);

        // View tournaments button (if has tournaments)
        if (tournamentsCount > 0) {
            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton viewBtn = new InlineKeyboardButton();
            viewBtn.setText("📋 Turnirlarni ko'rish (" + tournamentsCount + ")");
            viewBtn.setCallbackData("view_organizer_tournaments:" + organizerId);
            row2.add(viewBtn);
            rows.add(row2);
        }

        // Back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("⬅️ Ortga");
        backBtn.setCallbackData("back_to_organizers");
        backRow.add(backBtn);
        rows.add(backRow);

        markup.setKeyboard(rows);
        return markup;
    }

    private void startImpersonation(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        Long adminId = update.getCallbackQuery().getFrom().getId();
        String callbackData = update.getCallbackQuery().getData();
        Long organizerId = Long.parseLong(callbackData.split(":")[1]);

        try {
            User organizer = userService.getUserById(organizerId)
                    .orElseThrow(() -> new IllegalArgumentException("Organizer not found"));

            // Use ActorContextService for impersonation (centralized identity resolution)
            boolean success = actorContextService.startImpersonation(adminId, organizerId);
            
            if (!success) {
                bot.editMessage(chatId, messageId, "❌ Impersonation ni boshlash mumkin emas");
                return;
            }

            String organizerName = organizer.getUsername() != null ? "@" + organizer.getUsername() : organizer.getFirstName();
            String message = "✅ Siz endi tashkilotchi sifatida kirgansiz!\n\n" +
                    "👤 Tashkilotchi: " + organizerName + "\n\n" +
                    "Siz endi ushbu tashkilotchining barcha turnirlarini boshqarishingiz mumkin.\n" +
                    "Profil, turnirlar va boshqa buyruqlar tashkilotchi nomidan ishlaydi.\n\n" +
                    "Chiqish uchun pastdagi \"🚪 Chiqish\" tugmasini bosing.";

            // Delete old message and send new with organizer keyboard
            bot.deleteMessage(chatId, messageId);
            
            // Send message with organizer keyboard (includes "Chiqish" button)
            bot.sendMessage(chatId, message, KeyboardFactory.createOrganizerMenuWithExit());

            log.info("Admin {} started impersonating organizer {} via ActorContextService", adminId, organizerId);

        } catch (Exception e) {
            log.error("Error starting impersonation", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    private InlineKeyboardMarkup createImpersonationActiveKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Manage tournaments button
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton manageBtn = new InlineKeyboardButton();
        manageBtn.setText("🔧 Turnirlarni boshqarish");
        manageBtn.setCallbackData("manage_impersonated_tournaments");
        row1.add(manageBtn);
        rows.add(row1);

        // Exit impersonation button
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton exitBtn = new InlineKeyboardButton();
        exitBtn.setText("🚪 Chiqish");
        exitBtn.setCallbackData("exit_impersonation");
        row2.add(exitBtn);
        rows.add(row2);

        markup.setKeyboard(rows);
        return markup;
    }

    private void exitImpersonation(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        Long adminId = update.getCallbackQuery().getFrom().getId();

        // Use ActorContextService to exit impersonation
        actorContextService.exitImpersonation(adminId);

        String message = "✅ Siz tashkilotchi profilidan chiqdingiz\n\n" +
                "Endi o'zingiz (admin) sifatida ishlayapsiz.";

        // Delete old message and send new with admin keyboard
        bot.deleteMessage(chatId, messageId);
        bot.sendMessage(chatId, message, KeyboardFactory.createAdminMenu());
        
        log.info("Admin {} exited impersonation via ActorContextService", adminId);
    }

    private void viewOrganizerTournaments(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long organizerId = Long.parseLong(callbackData.split(":")[1]);

        try {
            User organizer = userService.getUserById(organizerId)
                    .orElseThrow(() -> new IllegalArgumentException("Organizer not found"));

            List<Tournament> tournaments = tournamentRepository.findByCreatedBy(organizer);

            if (tournaments.isEmpty()) {
                bot.editMessage(chatId, messageId, "📋 Ushbu tashkilotchida turnirlar yo'q");
                return;
            }

            StringBuilder message = new StringBuilder();
            message.append("🏆 Tashkilotchi turnirlari\n");
            message.append("Tashkilotchi: ").append(organizer.getFullName()).append("\n\n");
            
            for (int i = 0; i < tournaments.size(); i++) {
                Tournament t = tournaments.get(i);
                message.append(i + 1).append(". ");
                message.append(t.getName()).append(" (");
                message.append(t.getType() == com.chempionat.bot.domain.enums.TournamentType.LEAGUE ? "Liga" : "Pley-off");
                message.append(")\n");
                message.append("   Status: ").append(t.getIsActive() ? "✅ Aktiv" : "⏸️ Faol emas").append("\n");
            }

            bot.editMessage(chatId, messageId, message.toString());
            
        } catch (Exception e) {
            log.error("Error viewing organizer tournaments", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    private void manageImpersonatedTournaments(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        Long adminId = update.getCallbackQuery().getFrom().getId();

        // Use ActorContextService to check impersonation
        if (!actorContextService.isImpersonating(adminId)) {
            bot.editMessage(chatId, messageId, "❌ Siz hech kimni impersonate qilmayapsiz");
            return;
        }

        Long impersonatedUserId = actorContextService.getImpersonatedOrganizerId(adminId);
        
        try {
            User organizer = userService.getUserById(impersonatedUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Organizer not found"));

            List<Tournament> tournaments = tournamentRepository.findByCreatedBy(organizer);

            if (tournaments.isEmpty()) {
                bot.editMessage(chatId, messageId, 
                    "📋 Ushbu tashkilotchida turnirlar yo'q\n\n" +
                    "Yangi turnir yaratish uchun /createtournament buyrug'ini yuboring");
                return;
            }

            StringBuilder message = new StringBuilder();
            message.append("🏆 Boshqarish\n");
            message.append("Tashkilotchi: ").append(organizer.getFullName()).append("\n\n");
            message.append("Turnirlarni boshqarish uchun /managetournaments buyrug'ini yuboring");

            bot.editMessage(chatId, messageId, message.toString());
            
        } catch (Exception e) {
            log.error("Error managing impersonated tournaments", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    @Override
    public String getCommandName() {
        return "/manageorganizer";
    }
}
