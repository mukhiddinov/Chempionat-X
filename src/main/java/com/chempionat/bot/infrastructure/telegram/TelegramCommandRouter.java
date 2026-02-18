package com.chempionat.bot.infrastructure.telegram;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramCommandRouter {

    private final List<TelegramCommand> commands;
    private final Map<String, TelegramCommand> commandMap = new HashMap<>();

    @PostConstruct
    public void init() {
        commands.forEach(cmd -> {
            commandMap.put(cmd.getCommandName(), cmd);
            log.info("Registered command: {}", cmd.getCommandName());
        });
    }

    public void handleUpdate(Update update, TelegramBot bot) {
        String messageText = update.getMessage().getText();
        Long userId = update.getMessage().getFrom().getId();
        
        // Map keyboard button texts to commands
        messageText = mapButtonTextToCommand(messageText);
        
        // Check if user is in a conversation flow
        UserContext context = UserContext.get(userId);
        if (context.getCurrentCommand() != null) {
            TelegramCommand activeCommand = commandMap.get(context.getCurrentCommand());
            if (activeCommand != null) {
                log.debug("Continuing conversation for command: {}", context.getCurrentCommand());
                activeCommand.execute(update, bot);
                return;
            }
        }
        
        String command = extractCommand(messageText);

        TelegramCommand telegramCommand = commandMap.get(command);
        if (telegramCommand != null) {
            log.debug("Executing command: {} for user: {}", command, userId);
            telegramCommand.execute(update, bot);
        } else {
            log.debug("Unknown command or text: {}", command);
            handleUnknownCommand(update, bot);
        }
    }

    public void handleCallbackQuery(Update update, TelegramBot bot) {
        String callbackData = update.getCallbackQuery().getData();
        Long userId = update.getCallbackQuery().getFrom().getId();
        
        log.debug("Handling callback: {} for user: {}", callbackData, userId);
        
        // Check if user is in an active conversation first
        UserContext context = UserContext.get(userId);
        if (context.getCurrentCommand() != null) {
            // If in conversation and callback should go to that command, route it there
            if (shouldRouteToActiveCommand(callbackData, context.getCurrentCommand())) {
                TelegramCommand activeCommand = commandMap.get(context.getCurrentCommand());
                if (activeCommand != null) {
                    log.debug("Routing callback to active conversation: {}", context.getCurrentCommand());
                    activeCommand.execute(update, bot);
                    return;
                }
            }
        }
        
        // Extract command from callback data (format: command:params or just command)
        String command = extractCallbackCommand(callbackData);
        
        TelegramCommand telegramCommand = commandMap.get(command);
        if (telegramCommand != null) {
            telegramCommand.execute(update, bot);
        } else {
            log.warn("No handler found for callback: {}, trying full data as command", callbackData);
            // Try the full callback data as a command trigger
            // This handles cases like "tournamenttype:LEAGUE" which should go to /createtournament
            if (callbackData.startsWith("tournamenttype:") || 
                callbackData.startsWith("tournament:") ||
                callbackData.startsWith("jointournament:") ||
                callbackData.startsWith("standings:") ||
                callbackData.startsWith("schedule:") ||
                callbackData.startsWith("submitresult:") ||
                callbackData.startsWith("resubmit:") ||
                callbackData.startsWith("approve:") ||
                callbackData.startsWith("reject:") ||
                callbackData.startsWith("approveorganizer:") ||
                callbackData.startsWith("rejectorganizer:") ||
                callbackData.startsWith("view_tournament:") ||
                callbackData.startsWith("rounds:") ||
                callbackData.startsWith("autostart:") ||
                callbackData.startsWith("manage_tournament:") ||
                callbackData.startsWith("start_tournament:") ||
                callbackData.startsWith("share_tournament:") ||
                callbackData.startsWith("my_matches:") ||
                callbackData.startsWith("submit_result_user:") ||
                callbackData.startsWith("edit_matches:") ||
                callbackData.startsWith("select_round:") ||
                callbackData.startsWith("edit_match:") ||
                callbackData.startsWith("edit_home_score:") ||
                callbackData.startsWith("edit_away_score:") ||
                callbackData.equals("back_to_rounds") ||
                callbackData.startsWith("back_to_matches:") ||
                callbackData.startsWith("delete_tournament:") ||
                callbackData.startsWith("confirm_delete:") ||
                callbackData.startsWith("cancel_delete:") ||
                callbackData.startsWith("manage_organizer:") ||
                callbackData.startsWith("impersonate:") ||
                callbackData.equals("exit_impersonation") ||
                callbackData.equals("back_to_organizers") ||
                callbackData.equals("back_to_manage_list") ||
                callbackData.startsWith("standingsimg:") ||
                callbackData.startsWith("fixturesimg:") ||
                callbackData.startsWith("roundimg:") ||
                callbackData.equals("noop") ||
                callbackData.startsWith("page:")) {
                
                // These callbacks should be handled by their respective commands
                String handlerCommand = getHandlerForCallback(callbackData);
                telegramCommand = commandMap.get(handlerCommand);
                if (telegramCommand != null) {
                    log.debug("Routing callback to handler: {}", handlerCommand);
                    telegramCommand.execute(update, bot);
                } else {
                    log.error("No handler command found for callback: {}", callbackData);
                }
            }
        }
    }

    public void handlePhotoMessage(Update update, TelegramBot bot) {
        Long userId = update.getMessage().getFrom().getId();
        
        // Check if user is in a conversation flow expecting a photo
        UserContext context = UserContext.get(userId);
        if (context.getCurrentCommand() != null) {
            TelegramCommand activeCommand = commandMap.get(context.getCurrentCommand());
            if (activeCommand != null) {
                log.debug("Handling photo for command: {}", context.getCurrentCommand());
                activeCommand.execute(update, bot);
                return;
            }
        }
        
        bot.sendMessage(update.getMessage().getChatId(), 
            "I received your photo, but I'm not sure what to do with it. Please use a command first.");
    }

    private void handleUnknownCommand(Update update, TelegramBot bot) {
        bot.sendMessage(update.getMessage().getChatId(), 
            "Unknown command. Available commands:\n\n" +
            "🎮 /start - Start using the bot\n" +
            "👤 /profile - View your profile\n" +
            "🏆 /tournaments - View all tournaments\n" +
            "➕ /createtournament - Create a new tournament (Admin)\n" +
            "🎯 /mytournaments - View your tournaments\n" +
            "📅 /todaysmatches - View today's matches\n" +
            "📊 /standings - View tournament standings");
    }

    private String extractCommand(String messageText) {
        if (messageText.startsWith("/")) {
            int spaceIndex = messageText.indexOf(' ');
            if (spaceIndex > 0) {
                return messageText.substring(0, spaceIndex);
            }
            return messageText;
        }
        return messageText;
    }

    private String extractCallbackCommand(String callbackData) {
        int colonIndex = callbackData.indexOf(':');
        if (colonIndex > 0) {
            return "/" + callbackData.substring(0, colonIndex);
        }
        return "/" + callbackData;
    }
    
    private String mapButtonTextToCommand(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();

        // Handle buttons that might include extra spaces or hidden characters
        if (normalized.startsWith("🏆 Mening turnirlarim")) {
            return "/mytournaments";
        }

        return switch (normalized) {
            case "🏆 Turnirlar" -> "/tournaments";
            case "🎯 Turnirga qo'shilish" -> "/tournaments";
            case "📅 Bugungi o'yinlarim" -> "/todaysmatches";
            case "⚙️ Profil" -> "/profile";
            case "🔧 Boshqarish" -> "/managetournaments";
            case "👥 Tashkilotchilar" -> "/organizers";
            case "➕ Turnir yaratish" -> "/createtournament";
            case "🚪 Chiqish" -> "/exitimpersonation";
            default -> normalized;
        };
    }
    
    private String getHandlerForCallback(String callbackData) {
        if (callbackData.startsWith("tournamenttype:")) {
            return "/createtournament";
        } else if (callbackData.startsWith("rounds:") && callbackData.split(":").length == 2 && callbackData.split(":")[1].matches("\\d+")) {
            // rounds:123 (tournament rounds view) vs rounds:1 (number of rounds selection)
            try {
                Long.parseLong(callbackData.split(":")[1]);
                return "/fixturesimg"; // Tournament rounds view - now uses image rendering
            } catch (NumberFormatException e) {
                return "/createtournament"; // Number of rounds selection
            }
        } else if (callbackData.startsWith("tournament:")) {
            return "/tournaments";
        } else if (callbackData.startsWith("jointournament:")) {
            return "/jointournament";
        } else if (callbackData.startsWith("standings:")) {
            return "/standingsimg";
        } else if (callbackData.startsWith("schedule:")) {
            return "/fixturesimg";
        } else if (callbackData.startsWith("submitresult:")) {
            return "/submitresult";
        } else if (callbackData.startsWith("approve:")) {
            return "/approve";
        } else if (callbackData.startsWith("reject:")) {
            return "/reject";
        } else if (callbackData.startsWith("approveorganizer:")) {
            return "/approveorganizer";
        } else if (callbackData.startsWith("rejectorganizer:")) {
            return "/rejectorganizer";
        } else if (callbackData.startsWith("view_tournament:")) {
            return "/view_tournament";
        } else if (callbackData.startsWith("autostart:")) {
            return "/createtournament";
        } else if (callbackData.startsWith("my_matches:")) {
            return "/mymatches";
        } else if (callbackData.startsWith("submit_result_user:") ||
                   callbackData.startsWith("submitresult:") ||
                   callbackData.startsWith("resubmit:")) {
            return "/submitresult";
        } else if (callbackData.startsWith("manage_tournament:") ||
                   callbackData.startsWith("start_tournament:") ||
                   callbackData.startsWith("share_tournament:") ||
                   callbackData.equals("back_to_manage_list")) {
            return "/managetournaments";
        } else if (callbackData.startsWith("delete_tournament:") ||
                   callbackData.startsWith("confirm_delete:") ||
                   callbackData.startsWith("cancel_delete:")) {
            return "/deletetournament";
        } else if (callbackData.startsWith("edit_matches:") ||
                   callbackData.startsWith("select_round:") ||
                   callbackData.startsWith("edit_match:") ||
                   callbackData.startsWith("edit_home_score:") ||
                   callbackData.startsWith("edit_away_score:") ||
                   callbackData.equals("back_to_rounds") ||
                   callbackData.startsWith("back_to_matches:")) {
            return "/editmatches";
        } else if (callbackData.startsWith("manage_organizer:") ||
                   callbackData.startsWith("impersonate:") ||
                   callbackData.startsWith("view_organizer_tournaments:") ||
                   callbackData.equals("exit_impersonation") ||
                   callbackData.equals("back_to_organizers") ||
                   callbackData.equals("manage_impersonated_tournaments")) {
            return "/manageorganizer";
        } else if (callbackData.equals("my_tournaments")) {
            return "/mytournaments";
        } else if (callbackData.startsWith("page:")) {
            // Handle pagination callbacks
            return extractPageHandler(callbackData);
        } else if (callbackData.startsWith("standingsimg:")) {
            return "/standingsimg";
        } else if (callbackData.startsWith("fixturesimg:")) {
            return "/fixturesimg";
        } else if (callbackData.startsWith("roundimg:")) {
            return "/roundimg";
        } else if (callbackData.equals("noop")) {
            return null; // Ignore no-op callbacks
        }
        return null;
    }
    
    private String extractPageHandler(String callbackData) {
        // Format: page:context:number
        // Context examples: mytournaments, matches_round_1, organizers, manage_tournaments
        String[] parts = callbackData.split(":");
        if (parts.length >= 2) {
            String context = parts[1];
            if (context.equals("mytournaments")) {
                return "/mytournaments";
            } else if (context.equals("manage_tournaments")) {
                return "/managetournaments";
            } else if (context.startsWith("matches_round")) {
                return "/editmatches";
            } else if (context.equals("organizers")) {
                return "/organizers"; // Will be created later
            }
        }
        return null;
    }
    
    private boolean shouldRouteToActiveCommand(String callbackData, String activeCommand) {
        // Route callbacks to active command if they're part of that command's flow
        if (activeCommand.equals("/createtournament")) {
            return callbackData.startsWith("tournamenttype:") ||
                   callbackData.startsWith("rounds:") ||
                   callbackData.startsWith("autostart:");
        } else if (activeCommand.equals("/submitresult")) {
            return callbackData.startsWith("submitresult:");
        } else if (activeCommand.equals("/editmatches")) {
            return callbackData.startsWith("edit_matches:") ||
                   callbackData.startsWith("select_round:") ||
                   callbackData.startsWith("edit_match:") ||
                   callbackData.startsWith("edit_home_score:") ||
                   callbackData.startsWith("edit_away_score:") ||
                   callbackData.equals("back_to_rounds") ||
                   callbackData.startsWith("back_to_matches:");
        }
        return false;
    }
}
