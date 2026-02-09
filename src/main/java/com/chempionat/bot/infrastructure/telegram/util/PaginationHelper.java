package com.chempionat.bot.infrastructure.telegram.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for creating paginated inline keyboards
 */
public class PaginationHelper {

    private static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * Create paginated inline keyboard
     */
    public static <T> InlineKeyboardMarkup createPaginatedKeyboard(
            List<T> items,
            int currentPage,
            int pageSize,
            java.util.function.Function<T, String> buttonTextExtractor,
            java.util.function.Function<T, String> callbackDataExtractor,
            String contextPrefix) {
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        int totalPages = (int) Math.ceil((double) items.size() / pageSize);
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, items.size());

        // Add item buttons
        for (int i = startIndex; i < endIndex; i++) {
            T item = items.get(i);
            List<InlineKeyboardButton> row = new ArrayList<>();
            
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(buttonTextExtractor.apply(item))
                    .callbackData(callbackDataExtractor.apply(item))
                    .build();
            
            row.add(button);
            rows.add(row);
        }

        // Add pagination controls if needed
        if (totalPages > 1) {
            List<InlineKeyboardButton> paginationRow = new ArrayList<>();
            
            if (currentPage > 0) {
                InlineKeyboardButton prevButton = InlineKeyboardButton.builder()
                        .text("◀️ Prev")
                        .callbackData(String.format("page:%s:%d", contextPrefix, currentPage - 1))
                        .build();
                paginationRow.add(prevButton);
            }
            
            InlineKeyboardButton pageInfo = InlineKeyboardButton.builder()
                    .text(String.format("📄 %d/%d", currentPage + 1, totalPages))
                    .callbackData("noop")
                    .build();
            paginationRow.add(pageInfo);
            
            if (currentPage < totalPages - 1) {
                InlineKeyboardButton nextButton = InlineKeyboardButton.builder()
                        .text("Next ▶️")
                        .callbackData(String.format("page:%s:%d", contextPrefix, currentPage + 1))
                        .build();
                paginationRow.add(nextButton);
            }
            
            rows.add(paginationRow);
        }

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Create paginated keyboard with default page size
     */
    public static <T> InlineKeyboardMarkup createPaginatedKeyboard(
            List<T> items,
            int currentPage,
            java.util.function.Function<T, String> buttonTextExtractor,
            java.util.function.Function<T, String> callbackDataExtractor,
            String contextPrefix) {
        return createPaginatedKeyboard(items, currentPage, DEFAULT_PAGE_SIZE, 
                buttonTextExtractor, callbackDataExtractor, contextPrefix);
    }

    /**
     * Create paginated keyboard with back button
     */
    public static <T> InlineKeyboardMarkup createPaginatedKeyboardWithBack(
            List<T> items,
            int currentPage,
            int pageSize,
            java.util.function.Function<T, String> buttonTextExtractor,
            java.util.function.Function<T, String> callbackDataExtractor,
            String contextPrefix,
            String backButtonText,
            String backCallbackData) {
        
        InlineKeyboardMarkup keyboard = createPaginatedKeyboard(
                items, currentPage, pageSize, buttonTextExtractor, callbackDataExtractor, contextPrefix);
        
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        
        // Add back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .text(backButtonText)
                .callbackData(backCallbackData)
                .build();
        backRow.add(backButton);
        rows.add(backRow);
        
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Extract page number from pagination callback data
     */
    public static int extractPageNumber(String callbackData) {
        // Format: "page:context:number"
        String[] parts = callbackData.split(":");
        if (parts.length >= 3) {
            try {
                return Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Extract context from pagination callback data
     */
    public static String extractContext(String callbackData) {
        // Format: "page:context:number"
        String[] parts = callbackData.split(":");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "";
    }
}
