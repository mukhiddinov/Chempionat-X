package com.chempionat.bot.application.service;

import java.awt.*;

/**
 * Utility class for text rendering operations in images.
 * Provides text truncation with ellipsis and column alignment helpers.
 */
public final class TextRenderUtils {

    private TextRenderUtils() {
        // Utility class
    }

    /**
     * Truncate text with ellipsis to fit within a given pixel width.
     *
     * @param text     the text to truncate
     * @param maxWidth maximum width in pixels
     * @param fm       FontMetrics for measuring text width
     * @return truncated text with ellipsis if needed
     */
    public static String truncateWithEllipsis(String text, int maxWidth, FontMetrics fm) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "…";
        int ellipsisWidth = fm.stringWidth(ellipsis);

        StringBuilder truncated = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(truncated.toString() + c) + ellipsisWidth > maxWidth) {
                break;
            }
            truncated.append(c);
        }
        return truncated.toString().stripTrailing() + ellipsis;
    }

    /**
     * Truncate text with "..." to fit within a given character limit.
     *
     * @param text      the text to truncate
     * @param maxLength maximum character length
     * @return truncated text with "..." if needed
     */
    public static String truncateWithEllipsis(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Pad text to the left (right-align) within a given width.
     *
     * @param text  the text to pad
     * @param width total width
     * @return padded string
     */
    public static String padLeft(String text, int width) {
        if (text == null) text = "";
        return String.format("%" + width + "s", text);
    }

    /**
     * Pad text to the right (left-align) within a given width.
     *
     * @param text  the text to pad
     * @param width total width
     * @return padded string
     */
    public static String padRight(String text, int width) {
        if (text == null) text = "";
        return String.format("%-" + width + "s", text);
    }

    /**
     * Center text within a given width.
     *
     * @param text  the text to center
     * @param width total width
     * @return centered string with padding
     */
    public static String center(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) {
            return text;
        }
        int padding = (width - text.length()) / 2;
        int extra = (width - text.length()) % 2;
        return " ".repeat(padding) + text + " ".repeat(padding + extra);
    }

    /**
     * Format number with sign prefix (+/-).
     *
     * @param number the number to format
     * @return formatted string with sign
     */
    public static String formatWithSign(int number) {
        if (number > 0) {
            return "+" + number;
        }
        return String.valueOf(number);
    }

    /**
     * Format score as "X – Y" with en-dash.
     *
     * @param home home score
     * @param away away score
     * @return formatted score string
     */
    public static String formatScore(Integer home, Integer away) {
        if (home == null || away == null) {
            return "—";
        }
        return home + " – " + away;
    }

    /**
     * Format score as "X:Y" with colon.
     *
     * @param home home score
     * @param away away score
     * @return formatted score string
     */
    public static String formatScoreColon(Integer home, Integer away) {
        if (home == null || away == null) {
            return "-:-";
        }
        return home + ":" + away;
    }
}
