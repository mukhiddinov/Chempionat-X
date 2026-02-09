package com.chempionat.bot.infrastructure.telegram.commands;

import com.chempionat.bot.application.service.TournamentService;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.infrastructure.telegram.KeyboardFactory;
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
public class CreateTournamentCommand implements TelegramCommand {

    private final TournamentService tournamentService;
    private final UserService userService;

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId;
        Long userId;

        if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            userId = update.getMessage().getFrom().getId();
            
            UserContext context = UserContext.get(userId);
            
            // Check if this is the start of the command
            if (context.getCurrentCommand() == null || !context.getCurrentCommand().equals(getCommandName())) {
                startTournamentCreation(bot, chatId, userId, context);
            } else {
                handleConversation(bot, chatId, userId, context, update.getMessage().getText());
            }
            
        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            userId = update.getCallbackQuery().getFrom().getId();
            String callbackData = update.getCallbackQuery().getData();
            
            if (callbackData.startsWith("tournamenttype:")) {
                handleTypeSelection(bot, chatId, userId, callbackData, 
                                   update.getCallbackQuery().getMessage().getMessageId());
            } else if (callbackData.startsWith("rounds:")) {
                handleRoundsSelection(bot, chatId, userId, callbackData,
                                     update.getCallbackQuery().getMessage().getMessageId());
            } else if (callbackData.startsWith("autostart:")) {
                handleAutoStartSelection(bot, chatId, userId, callbackData,
                                        update.getCallbackQuery().getMessage().getMessageId());
            }
        }
    }

    private void startTournamentCreation(TelegramBot bot, Long chatId, Long userId, UserContext context) {
        // Check if user is admin or organizer
        Optional<User> userOpt = userService.getUserByTelegramId(userId);
        if (userOpt.isEmpty() || (userOpt.get().getRole() != Role.ADMIN && 
                                   userOpt.get().getRole() != Role.MODERATOR && 
                                   userOpt.get().getRole() != Role.ORGANIZER)) {
            bot.sendMessage(chatId, "❌ Faqat adminlar va tashkilotchilar turnir yaratishi mumkin.\n\n" +
                    "Tashkilotchi bo'lish uchun: /requestorganizer");
            return;
        }

        context.setCurrentCommand(getCommandName());
        context.setData("step", "name");
        
        bot.sendMessage(chatId, "🏆 Yangi turnir yaratish\n\nTurnir nomini kiriting:");
    }

    private void handleConversation(TelegramBot bot, Long chatId, Long userId, 
                                    UserContext context, String text) {
        String step = context.getDataAsString("step");

        switch (step) {
            case "name":
                context.setData("name", text);
                context.setData("step", "description");
                bot.sendMessage(chatId, "Turnir tavsifini kiriting (yoki /skip yozing):");
                break;

            case "description":
                if (!text.equals("/skip")) {
                    context.setData("description", text);
                }
                context.setData("step", "type");
                bot.sendMessage(chatId, "Turnir formatini tanlang:", 
                               KeyboardFactory.createTournamentTypeKeyboard());
                break;
            
            case "max_participants":
                if (text.equals("/skip")) {
                    // No max participants limit
                    context.setData("step", "number_of_rounds");
                    bot.sendMessage(chatId, "Turlar sonini tanlang:", 
                                   KeyboardFactory.createNumberOfRoundsKeyboard());
                    return;
                }
                try {
                    int maxParticipants = Integer.parseInt(text.trim());
                    if (maxParticipants < 2) {
                        bot.sendMessage(chatId, "❌ Kamida 2 ishtirokchi bo'lishi kerak. Qaytadan kiriting:");
                        return;
                    }
                    if (maxParticipants > 100) {
                        bot.sendMessage(chatId, "❌ Ko'pi bilan 100 ishtirokchi bo'lishi mumkin. Qaytadan kiriting:");
                        return;
                    }
                    context.setData("max_participants", maxParticipants);
                    context.setData("step", "number_of_rounds");
                    bot.sendMessage(chatId, "Turlar sonini tanlang:", 
                                   KeyboardFactory.createNumberOfRoundsKeyboard());
                } catch (NumberFormatException e) {
                    bot.sendMessage(chatId, "❌ Iltimos, raqam kiriting:");
                }
                break;

            default:
                bot.sendMessage(chatId, "Xatolik yuz berdi. /createtournament dan qayta boshlang.");
                context.clearData();
        }
    }

    private void handleTypeSelection(TelegramBot bot, Long chatId, Long userId, 
                                     String callbackData, Integer messageId) {
        UserContext context = UserContext.get(userId);
        String typeStr = callbackData.split(":")[1];
        TournamentType type = TournamentType.valueOf(typeStr);
        
        context.setData("type", type.name());
        context.setData("step", "max_participants");
        
        bot.editMessage(chatId, messageId, 
                       "Maksimal ishtirokchilar sonini kiriting (2-100):\n\n" +
                       "Yoki /skip yozing (cheklovsiz)");
    }
    
    private void handleRoundsSelection(TelegramBot bot, Long chatId, Long userId,
                                       String callbackData, Integer messageId) {
        UserContext context = UserContext.get(userId);
        String roundsStr = callbackData.split(":")[1];
        int rounds = Integer.parseInt(roundsStr);
        
        context.setData("number_of_rounds", rounds);
        context.setData("step", "auto_start");
        
        bot.editMessage(chatId, messageId,
                       "Ishtirokchilar to'lganda avtomatik boshlansinmi?",
                       KeyboardFactory.createAutoStartKeyboard());
    }
    
    private void handleAutoStartSelection(TelegramBot bot, Long chatId, Long userId,
                                         String callbackData, Integer messageId) {
        UserContext context = UserContext.get(userId);
        String autoStartStr = callbackData.split(":")[1];
        boolean autoStart = Boolean.parseBoolean(autoStartStr);
        
        context.setData("auto_start", autoStart);
        
        // Now create the tournament with all settings
        createTournamentWithSettings(bot, chatId, userId, messageId, context);
    }
    
    private void createTournamentWithSettings(TelegramBot bot, Long chatId, Long userId,
                                              Integer messageId, UserContext context) {
        try {
            Optional<User> userOpt = userService.getUserByTelegramId(userId);
            if (userOpt.isEmpty()) {
                bot.editMessage(chatId, messageId, "Foydalanuvchi topilmadi.");
                context.clearData();
                return;
            }

            User user = userOpt.get();
            String name = context.getDataAsString("name");
            String description = context.getDataAsString("description");
            TournamentType type = TournamentType.valueOf(context.getDataAsString("type"));
            Integer maxParticipants = context.getDataAsInteger("max_participants");
            Integer numberOfRounds = context.getDataAsInteger("number_of_rounds");
            Boolean autoStart = context.getDataAsBoolean("auto_start");

            // Create tournament
            Tournament tournament = tournamentService.createTournament(name, description, type, user);
            
            // Update with additional settings
            if (maxParticipants != null) {
                tournament.setMaxParticipants(maxParticipants);
            }
            if (numberOfRounds != null) {
                tournament.setNumberOfRounds(numberOfRounds);
            }
            if (autoStart != null) {
                tournament.setAutoStart(autoStart);
            }
            
            tournament = tournamentService.updateTournament(tournament);

            String message = buildSuccessMessage(tournament, description);
            bot.editMessage(chatId, messageId, message);
            
            log.info("Tournament created: id={}, name={}, type={}, maxParticipants={}, rounds={}, autoStart={}, creator={}", 
                     tournament.getId(), name, type, maxParticipants, numberOfRounds, autoStart, userId);

        } catch (Exception e) {
            log.error("Error creating tournament", e);
            bot.editMessage(chatId, messageId, "Xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
        } finally {
            context.clearData();
        }
    }
    
    private String buildSuccessMessage(Tournament tournament, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ Turnir muvaffaqiyatli yaratildi!\n\n");
        sb.append("🏆 Nomi: ").append(tournament.getName()).append("\n");
        sb.append("📊 Format: ").append(getTypeText(tournament.getType().name())).append("\n");
        
        if (description != null) {
            sb.append("📝 Tavsif: ").append(description).append("\n");
        }
        
        if (tournament.getMaxParticipants() != null) {
            sb.append("👥 Maksimal ishtirokchilar: ").append(tournament.getMaxParticipants()).append("\n");
        }
        
        if (tournament.getNumberOfRounds() != null) {
            sb.append("🔄 Turlar soni: ").append(tournament.getNumberOfRounds()).append("\n");
        }
        
        if (tournament.getAutoStart() != null && tournament.getAutoStart()) {
            sb.append("⚡ Avtomatik boshlanish: Yoqilgan\n");
        }
        
        sb.append("\n");
        sb.append("O'yinchilar endi /tournaments orqali qo'shilishlari mumkin.\n");
        
        if (tournament.getAutoStart() != null && tournament.getAutoStart()) {
            sb.append("Ishtirokchilar to'lganda turnir avtomatik boshlanadi.\n");
        } else {
            sb.append("Turnirni boshlash uchun: /starttournament ").append(tournament.getId());
        }
        
        return sb.toString();
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
        return "/createtournament";
    }
}
