package com.chempionat.bot.application.service;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Base utility class for rendering dark-themed table images.
 */
public abstract class ImageRenderer {

    // Image dimensions
    protected static final int IMAGE_WIDTH = 1080;
    protected static final int PADDING = 30;
    protected static final int ROW_HEIGHT = 50;
    protected static final int HEADER_HEIGHT = 70;
    
    // Dark theme colors
    protected static final Color BACKGROUND_COLOR = new Color(0x1a, 0x1a, 0x2e);
    protected static final Color HEADER_BG_COLOR = new Color(0x16, 0x21, 0x3e);
    protected static final Color ROW_EVEN_COLOR = new Color(0x1f, 0x1f, 0x38);
    protected static final Color ROW_ODD_COLOR = new Color(0x25, 0x25, 0x45);
    protected static final Color TEXT_COLOR = Color.WHITE;
    protected static final Color HEADER_TEXT_COLOR = new Color(0x00, 0xd9, 0xff);
    protected static final Color BORDER_COLOR = new Color(0x3a, 0x3a, 0x5a);
    protected static final Color GOLD_COLOR = new Color(0xff, 0xd7, 0x00);
    protected static final Color SILVER_COLOR = new Color(0xc0, 0xc0, 0xc0);
    protected static final Color BRONZE_COLOR = new Color(0xcd, 0x7f, 0x32);
    protected static final Color SCORE_COLOR = new Color(0x00, 0xff, 0x88);
    
    // Fonts
    protected static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 28);
    protected static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 18);
    protected static final Font DATA_FONT = new Font("SansSerif", Font.PLAIN, 16);
    protected static final Font FOOTER_FONT = new Font("SansSerif", Font.ITALIC, 14);

    /**
     * Create a buffered image with proper rendering hints.
     */
    protected BufferedImage createImage(int height) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // Enable anti-aliasing for smooth text
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        // Fill background
        g2d.setColor(BACKGROUND_COLOR);
        g2d.fillRect(0, 0, IMAGE_WIDTH, height);
        
        g2d.dispose();
        return image;
    }

    /**
     * Draw centered text.
     */
    protected void drawCenteredText(Graphics2D g2d, String text, int x, int y, int width) {
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textX = x + (width - textWidth) / 2;
        g2d.drawString(text, textX, y);
    }

    /**
     * Draw left-aligned text.
     */
    protected void drawLeftText(Graphics2D g2d, String text, int x, int y) {
        g2d.drawString(text, x, y);
    }

    /**
     * Draw right-aligned text.
     */
    protected void drawRightText(Graphics2D g2d, String text, int x, int y, int width) {
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, x + width - textWidth, y);
    }

    /**
     * Draw a horizontal line.
     */
    protected void drawHorizontalLine(Graphics2D g2d, int y) {
        g2d.setColor(BORDER_COLOR);
        g2d.drawLine(PADDING, y, IMAGE_WIDTH - PADDING, y);
    }

    /**
     * Get position color for top 3 positions.
     */
    protected Color getPositionColor(int position) {
        return switch (position) {
            case 1 -> GOLD_COLOR;
            case 2 -> SILVER_COLOR;
            case 3 -> BRONZE_COLOR;
            default -> TEXT_COLOR;
        };
    }

    /**
     * Truncate text to fit within a given width.
     */
    protected String truncateText(String text, int maxWidth, FontMetrics fm) {
        if (text == null) return "";
        if (fm.stringWidth(text) <= maxWidth) return text;
        
        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        
        StringBuilder truncated = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(truncated.toString() + c) + ellipsisWidth > maxWidth) {
                break;
            }
            truncated.append(c);
        }
        return truncated + ellipsis;
    }
}
