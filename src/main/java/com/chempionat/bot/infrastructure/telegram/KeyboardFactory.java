package com.chempionat.bot.infrastructure.telegram;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for creating Telegram keyboards
 */
public class KeyboardFactory {

    /**
     * Creates the main menu keyboard for USER (without My Tournaments)
     */
    public static ReplyKeyboardMarkup createUserMenuWithoutTournaments() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("⚙️ Profil"));
        row1.add(new KeyboardButton("🎯 Turnirga qo'shilish"));
        rows.add(row1);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates the main menu keyboard for USER (with My Tournaments)
     */
    public static ReplyKeyboardMarkup createUserMenuWithTournaments() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("\uD83C\uDFC6 Mening turnirlarim"));
        row1.add(new KeyboardButton("⚙️ Profil"));
        rows.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🎯 Turnirga qo'shilish"));
        rows.add(row2);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates the main menu keyboard for ORGANIZER
     */
    public static ReplyKeyboardMarkup createOrganizerMenu() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("\uD83C\uDFC6 Mening turnirlarim"));
        row1.add(new KeyboardButton("⚙️ Profil"));
        rows.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🔧 Boshqarish"));
        row2.add(new KeyboardButton("➕ Turnir yaratish"));
        rows.add(row2);
        
        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("🎯 Turnirga qo'shilish"));
        rows.add(row3);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates the main menu keyboard for ORGANIZER with Exit button (for admin impersonation)
     */
    public static ReplyKeyboardMarkup createOrganizerMenuWithExit() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("\uD83C\uDFC6 Mening turnirlarim"));
        row1.add(new KeyboardButton("⚙️ Profil"));
        rows.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🔧 Boshqarish"));
        row2.add(new KeyboardButton("➕ Turnir yaratish"));
        rows.add(row2);
        
        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("🎯 Turnirga qo'shilish"));
        row3.add(new KeyboardButton("🚪 Chiqish"));
        rows.add(row3);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates the main menu keyboard for ADMIN
     */
    public static ReplyKeyboardMarkup createAdminMenu() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("👥 Tashkilotchilar"));
        row1.add(new KeyboardButton("⚙️ Profil"));
        rows.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🎯 Turnirga qo'shilish"));
        rows.add(row2);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates the main menu keyboard (legacy - for backward compatibility)
     */
    public static ReplyKeyboardMarkup createMainMenuKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("🏆 Turnirlar"));
        row1.add(new KeyboardButton("📅 Bugungi o'yinlarim"));
        rows.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("⚙️ Profil"));
        rows.add(row2);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates inline keyboard for tournament selection
     */
    public static InlineKeyboardMarkup createTournamentListKeyboard(List<TournamentButton> tournaments) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (TournamentButton tournament : tournaments) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(tournament.getName())
                    .callbackData("tournament:" + tournament.getId())
                    .build();
            row.add(button);
            rows.add(row);
        }

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates inline keyboard for tournament actions
     */
    public static InlineKeyboardMarkup createTournamentActionsKeyboard(Long tournamentId, boolean isParticipant) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (!isParticipant) {
            List<InlineKeyboardButton> joinRow = new ArrayList<>();
            InlineKeyboardButton joinButton = InlineKeyboardButton.builder()
                    .text("✅ Qo'shilish")
                    .callbackData("jointournament:" + tournamentId)
                    .build();
            joinRow.add(joinButton);
            rows.add(joinRow);
        }

        List<InlineKeyboardButton> viewRow = new ArrayList<>();
        InlineKeyboardButton standingsButton = InlineKeyboardButton.builder()
                .text("📊 Jadval")
                .callbackData("standingsimg:" + tournamentId + ":0")
                .build();
        InlineKeyboardButton scheduleButton = InlineKeyboardButton.builder()
                .text("📅 Kalendar")
                .callbackData("fixturesimg:" + tournamentId + ":0")
                .build();
        viewRow.add(standingsButton);
        viewRow.add(scheduleButton);
        rows.add(viewRow);

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .text("⬅️ Ortga")
                .callbackData("tournaments:list")
                .build();
        backRow.add(backButton);
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates inline keyboard for match result submission
     */
    public static InlineKeyboardMarkup createMatchActionsKeyboard(Long matchId, boolean isHome) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (isHome) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton submitButton = InlineKeyboardButton.builder()
                    .text("🏟 Natijani yuborish")
                    .callbackData("submitresult:" + matchId)
                    .build();
            row.add(submitButton);
            rows.add(row);
        }

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates inline keyboard for admin result approval
     */
    public static InlineKeyboardMarkup createResultApprovalKeyboard(Long resultId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton approveButton = InlineKeyboardButton.builder()
                .text("✅ Tasdiqlash")
                .callbackData("approve:" + resultId)
                .build();
        
        InlineKeyboardButton rejectButton = InlineKeyboardButton.builder()
                .text("❌ Rad etish")
                .callbackData("reject:" + resultId)
                .build();

        row.add(approveButton);
        row.add(rejectButton);
        rows.add(row);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates inline keyboard for tournament type selection
     */
    public static InlineKeyboardMarkup createTournamentTypeKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton leagueButton = InlineKeyboardButton.builder()
                .text("🏆 Liga formati")
                .callbackData("tournamenttype:LEAGUE")
                .build();
        row1.add(leagueButton);
        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton playoffButton = InlineKeyboardButton.builder()
                .text("🥇 Olimpiya tizimi")
                .callbackData("tournamenttype:PLAYOFF")
                .build();
        row2.add(playoffButton);
        rows.add(row2);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates inline keyboard for tournament details with rounds button
     */
    public static InlineKeyboardMarkup createTournamentDetailsKeyboard(Long tournamentId, boolean isLeague) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (isLeague) {
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton standingsButton = InlineKeyboardButton.builder()
                    .text("📊 Jadval")
                    .callbackData("standingsimg:" + tournamentId + ":0")
                    .build();
            row1.add(standingsButton);
            rows.add(row1);
        }

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton roundsButton = InlineKeyboardButton.builder()
                .text("📅 Turlar")
                .callbackData("fixturesimg:" + tournamentId + ":0")
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

    /**
     * Creates inline keyboard for match score editing
     */
    public static InlineKeyboardMarkup createMatchScoreEditKeyboard(Long matchId, Integer homeScore, Integer awayScore) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton homeButton = InlineKeyboardButton.builder()
                .text(String.format("Uy: %d", homeScore != null ? homeScore : 0))
                .callbackData("edit_home_score:" + matchId)
                .build();
        
        InlineKeyboardButton awayButton = InlineKeyboardButton.builder()
                .text(String.format("Mehmon: %d", awayScore != null ? awayScore : 0))
                .callbackData("edit_away_score:" + matchId)
                .build();

        row.add(homeButton);
        row.add(awayButton);
        rows.add(row);

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .text("⬅️ Ortga")
                .callbackData("back_to_matches")
                .build();
        backRow.add(backButton);
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates inline keyboard for number of rounds selection
     */
    public static InlineKeyboardMarkup createNumberOfRoundsKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton oneRoundButton = InlineKeyboardButton.builder()
                .text("1 tur")
                .callbackData("rounds:1")
                .build();
        
        InlineKeyboardButton twoRoundsButton = InlineKeyboardButton.builder()
                .text("2 tur")
                .callbackData("rounds:2")
                .build();

        row.add(oneRoundButton);
        row.add(twoRoundsButton);
        rows.add(row);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Creates inline keyboard for auto-start selection
     */
    public static InlineKeyboardMarkup createAutoStartKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton yesButton = InlineKeyboardButton.builder()
                .text("✅ Ha")
                .callbackData("autostart:yes")
                .build();
        
        InlineKeyboardButton noButton = InlineKeyboardButton.builder()
                .text("❌ Yo'q")
                .callbackData("autostart:no")
                .build();

        row.add(yesButton);
        row.add(noButton);
        rows.add(row);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * DTO for tournament button
     */
    public static class TournamentButton {
        private final Long id;
        private final String name;

        public TournamentButton(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
