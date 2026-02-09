package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.TeamRepository;
import com.chempionat.bot.domain.repository.TournamentRepository;
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

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ManageTournamentsCommand implements TelegramCommand {

    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final UserService userService;
    private final TournamentService tournamentService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = extractChatId(update);
        Long userId = extractUserId(update);

        // Check if user is organizer or admin
        Optional<User> userOpt = userService.getUserByTelegramId(userId);
        if (userOpt.isEmpty()) {
            bot.sendMessage(chatId, "❌ Foydalanuvchi topilmadi");
            return;
        }

        User user = userOpt.get();
        if (user.getRole() != Role.ORGANIZER && user.getRole() != Role.ADMIN && user.getRole() != Role.MODERATOR) {
            bot.sendMessage(chatId, "❌ Faqat tashkilotchilar turnirlarni boshqarishi mumkin");
            return;
        }

        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            if (callbackData.startsWith("manage_tournament:")) {
                handleTournamentManagement(update, bot);
            } else if (callbackData.startsWith("start_tournament:")) {
                handleStartTournament(update, bot);
            } else if (callbackData.startsWith("share_tournament:")) {
                handleShareTournament(update, bot);
            } else if (callbackData.startsWith("page:manage_tournaments:")) {
                handlePagination(update, bot, user);
            } else if (callbackData.equals("back_to_manage_list")) {
                // Show tournaments list again
                Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
                
                List<Tournament> tournaments = tournamentRepository.findByCreatedBy(user);
                String message = "\uD83C\uDFC6 Mening turnirlarim\n\nBoshqarish uchun turnirni tanlang:";
                
                InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboard(
                        tournaments,
                        0,
                        10,
                        this::formatTournamentButton,
                        t -> "manage_tournament:" + t.getId(),
                        "manage_tournaments"
                );
                
                bot.editMessage(chatId, messageId, message, keyboard);
            }
        } else {
            showTournamentsList(bot, chatId, user, 0);
        }
    }

    private void showTournamentsList(TelegramBot bot, Long chatId, User user, int page) {
        List<Tournament> tournaments = tournamentRepository.findByCreatedBy(user);

        if (tournaments.isEmpty()) {
            bot.sendMessage(chatId, "📭 Sizda hali turnirlar yo'q.\n\n" +
                    "Yangi turnir yaratish uchun: /createtournament");
            return;
        }

        String message = "\uD83C\uDFC6 Mening turnirlarim\n\n" +
                "Boshqarish uchun turnirni tanlang:";

        InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboard(
                tournaments,
                page,
                10,
                this::formatTournamentButton,
                t -> "manage_tournament:" + t.getId(),
                "manage_tournaments"
        );

        bot.sendMessage(chatId, message, keyboard);
    }

    private String formatTournamentButton(Tournament tournament) {
        String status = tournament.getIsActive() ? "✅" : "⏸️";
        String name = tournament.getName();
        if (name.length() > 25) {
            name = name.substring(0, 22) + "...";
        }
        return status + " " + name;
    }

    private void handleTournamentManagement(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long tournamentId = Long.parseLong(callbackData.split(":")[1]);

        try {
            Tournament tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            String message = buildTournamentManagementMessage(tournament);
            InlineKeyboardMarkup keyboard = createManagementKeyboard(tournamentId, tournament);

            bot.editMessage(chatId, messageId, message, keyboard);

        } catch (Exception e) {
            log.error("Error showing tournament management", e);
            bot.editMessage(chatId, messageId, "❌ Turnir topilmadi");
        }
    }

    private String buildTournamentManagementMessage(Tournament tournament) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 Turnirni boshqarish\n\n");
        sb.append("🏆 ").append(tournament.getName()).append("\n");
        sb.append("Status: ").append(tournament.getIsActive() ? "✅ Aktiv" : "⏸️ Faol emas").append("\n\n");

        if (tournament.getStartDate() != null) {
            sb.append("📅 Boshlanish: ")
              .append(tournament.getStartDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
              .append("\n");
        }

        // Count teams
        long teamsCount = teamRepository.findByTournament(tournament).size();
        sb.append("👥 Ishtirokchilar: ").append(teamsCount);
        if (tournament.getMaxParticipants() != null) {
            sb.append("/").append(tournament.getMaxParticipants());
        }
        sb.append("\n");

        if (tournament.getNumberOfRounds() != null) {
            sb.append("🔄 Turlar: ").append(tournament.getNumberOfRounds()).append("\n");
        }

        if (tournament.getAutoStart() != null && tournament.getAutoStart()) {
            sb.append("⚡ Avtomatik boshlanish: Yoqilgan\n");
        }

        sb.append("\n");
        sb.append("Quyidagi amallardan birini tanlang:");

        return sb.toString();
    }

    private InlineKeyboardMarkup createManagementKeyboard(Long tournamentId, Tournament tournament) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // View details
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton detailsBtn = new InlineKeyboardButton();
        detailsBtn.setText("📊 Ko'rish");
        detailsBtn.setCallbackData("view_tournament:" + tournamentId);
        row1.add(detailsBtn);
        rows.add(row1);

        // Share tournament button (always available)
        List<InlineKeyboardButton> shareRow = new ArrayList<>();
        InlineKeyboardButton shareBtn = new InlineKeyboardButton();
        shareBtn.setText("📤 Ulashish");
        shareBtn.setCallbackData("share_tournament:" + tournamentId);
        shareRow.add(shareBtn);
        rows.add(shareRow);

        // Start/Stop tournament
        if (!tournament.getIsActive()) {
            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton startBtn = new InlineKeyboardButton();
            startBtn.setText("▶️ Boshlash");
            startBtn.setCallbackData("start_tournament:" + tournamentId);
            row2.add(startBtn);
            rows.add(row2);
        }

        // Edit matches (only if active)
        if (tournament.getIsActive()) {
            List<InlineKeyboardButton> row3 = new ArrayList<>();
            InlineKeyboardButton editBtn = new InlineKeyboardButton();
            editBtn.setText("✏️ O'yinlarni tahrirlash");
            editBtn.setCallbackData("edit_matches:" + tournamentId);
            row3.add(editBtn);
            rows.add(row3);
        }

        // Delete tournament
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton();
        deleteBtn.setText("🗑️ O'chirish");
        deleteBtn.setCallbackData("delete_tournament:" + tournamentId);
        row4.add(deleteBtn);
        rows.add(row4);

        // Back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("⬅️ Ortga");
        backBtn.setCallbackData("back_to_manage_list");
        backRow.add(backBtn);
        rows.add(backRow);

        markup.setKeyboard(rows);
        return markup;
    }

    private void handleStartTournament(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long tournamentId = Long.parseLong(callbackData.split(":")[1]);
        Long userId = update.getCallbackQuery().getFrom().getId();

        try {
            Tournament tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            // Check if already active
            if (tournament.getIsActive()) {
                bot.editMessage(chatId, messageId, "❌ Turnir allaqachon boshlangan");
                return;
            }

            // Check minimum participants
            long teamsCount = teamRepository.findByTournament(tournament).size();
            if (teamsCount < 2) {
                bot.editMessage(chatId, messageId, 
                        "❌ Turnirni boshlash uchun kamida 2 ishtirokchi kerak.\n\n" +
                        "Hozirgi ishtirokchilar: " + teamsCount);
                return;
            }

            // Start the tournament
            tournamentService.startTournament(tournamentId);

            // Reload tournament to get updated data
            tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            String message = "✅ Turnir muvaffaqiyatli boshlandi!\n\n" +
                    "🏆 " + tournament.getName() + "\n" +
                    "👥 Ishtirokchilar: " + teamsCount + "\n" +
                    "📅 Boshlanish sanasi: " + 
                    tournament.getStartDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\n\n" +
                    "Barcha o'yinlar yaratildi va ishtirokchilarga xabar yuborildi.";

            // Show updated management keyboard (now with edit button, no start button)
            InlineKeyboardMarkup keyboard = createManagementKeyboard(tournamentId, tournament);
            bot.editMessage(chatId, messageId, message, keyboard);

            log.info("Tournament {} started by user {}, {} teams", tournamentId, userId, teamsCount);

        } catch (IllegalStateException e) {
            log.error("Error starting tournament: {}", e.getMessage());
            bot.editMessage(chatId, messageId, "❌ " + e.getMessage());
        } catch (Exception e) {
            log.error("Error starting tournament", e);
            bot.editMessage(chatId, messageId, "❌ Turnirni boshlashda xatolik yuz berdi");
        }
    }

    private void handlePagination(Update update, TelegramBot bot, User user) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();

        try {
            String[] parts = callbackData.split(":");
            int page = Integer.parseInt(parts[2]);

            List<Tournament> tournaments = tournamentRepository.findByCreatedBy(user);
            String message = "\uD83C\uDFC6 Mening turnirlarim\n\nBoshqarish uchun turnirni tanlang:";

            InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboard(
                    tournaments,
                    page,
                    10,
                    this::formatTournamentButton,
                    t -> "manage_tournament:" + t.getId(),
                    "manage_tournaments"
            );

            bot.editMessage(chatId, messageId, message, keyboard);

        } catch (Exception e) {
            log.error("Error handling pagination", e);
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

    private void handleShareTournament(Update update, TelegramBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackData = update.getCallbackQuery().getData();
        Long tournamentId = Long.parseLong(callbackData.split(":")[1]);

        try {
            Tournament tournament = tournamentService.getTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            // Generate deep link for joining tournament
            String botUsername = bot.getBotUsername();
            String shareLink = "https://t.me/" + botUsername + "?start=join_" + tournamentId;
            
            String message = "📤 Turnirga ulashish\n\n" +
                    "🏆 " + tournament.getName() + "\n\n" +
                    "Quyidagi havolani do'stlaringizga yuboring:\n" +
                    shareLink + "\n\n" +
                    "Yoki ushbu xabarni to'g'ridan-to'g'ri ulashing!";

            bot.editMessage(chatId, messageId, message);
            
            log.info("Share link generated for tournament {} by user {}", tournamentId, chatId);

        } catch (Exception e) {
            log.error("Error generating share link", e);
            bot.editMessage(chatId, messageId, "❌ Xatolik yuz berdi");
        }
    }

    @Override
    public String getCommandName() {
        return "/managetournaments";
    }
}
