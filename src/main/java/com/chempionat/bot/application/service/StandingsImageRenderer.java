package com.chempionat.bot.application.service;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders league standings table as a dark-themed PNG image.
 * Columns: #, Team, P, W, D, L, GF, GA, GD, Pts
 */
@Component
public class StandingsImageRenderer extends ImageRenderer {

    private static final int DEFAULT_ITEMS_PER_PAGE = 20;
    private int itemsPerPage = DEFAULT_ITEMS_PER_PAGE;
    
    // Column widths (total ~1020 to fit in 1080 with padding)
    private static final int POS_WIDTH = 45;
    private static final int TEAM_WIDTH = 280;
    private static final int STAT_WIDTH = 55;
    private static final int STAT_WIDTH_WIDE = 60;
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public void setItemsPerPage(int itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }

    /**
     * Render standings to PNG bytes.
     * 
     * @param tournamentName name of the tournament
     * @param standings list of all standings (already sorted)
     * @param page current page (0-indexed)
     * @return PNG image as byte array
     */
    public byte[] render(String tournamentName, List<TeamStanding> standings, int page) throws IOException {
        int totalPages = getTotalPages(standings.size());
        page = Math.max(0, Math.min(page, totalPages - 1));
        
        int startIdx = page * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, standings.size());
        List<TeamStanding> pageStandings = standings.subList(startIdx, endIdx);
        
        // Calculate image height
        int headerCardHeight = 90;
        int tableHeight = HEADER_HEIGHT + (pageStandings.size() * ROW_HEIGHT);
        int footerHeight = 50;
        int totalHeight = headerCardHeight + tableHeight + footerHeight + PADDING * 2;
        
        BufferedImage image = createImage(totalHeight);
        Graphics2D g2d = image.createGraphics();
        
        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        int y = PADDING;
        
        // Draw header card
        y = drawHeaderCard(g2d, tournamentName, page + 1, totalPages, y);
        
        // Draw table header
        drawTableHeader(g2d, y);
        y += HEADER_HEIGHT;
        
        // Draw rows
        for (int i = 0; i < pageStandings.size(); i++) {
            TeamStanding standing = pageStandings.get(i);
            int position = startIdx + i + 1;
            boolean isEven = i % 2 == 0;
            boolean isTopThree = position <= 3;
            drawRow(g2d, standing, position, y, isEven, isTopThree);
            y += ROW_HEIGHT;
        }
        
        // Draw footer with timestamp
        y += 10;
        drawFooter(g2d, y);
        
        g2d.dispose();
        
        return toBytes(image);
    }

    private int drawHeaderCard(Graphics2D g2d, String tournamentName, int currentPage, int totalPages, int y) {
        // Header card background
        g2d.setColor(HEADER_BG_COLOR);
        g2d.fillRoundRect(PADDING, y, IMAGE_WIDTH - PADDING * 2, 70, 15, 15);
        
        // Tournament name
        g2d.setFont(TITLE_FONT);
        g2d.setColor(TEXT_COLOR);
        String title = tournamentName + " — Standings";
        drawLeftText(g2d, title, PADDING + 20, y + 42);
        
        // Page indicator
        g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2d.setColor(HEADER_TEXT_COLOR);
        String pageText = "Page " + currentPage + "/" + totalPages;
        drawRightText(g2d, pageText, PADDING, y + 42, IMAGE_WIDTH - PADDING * 2 - 20);
        
        return y + 80;
    }

    private void drawTableHeader(Graphics2D g2d, int y) {
        // Header background
        g2d.setColor(new Color(0x0f, 0x0f, 0x23));
        g2d.fillRect(PADDING, y, IMAGE_WIDTH - PADDING * 2, HEADER_HEIGHT);
        
        g2d.setFont(HEADER_FONT);
        g2d.setColor(HEADER_TEXT_COLOR);
        
        int x = PADDING + 10;
        int textY = y + (HEADER_HEIGHT + g2d.getFontMetrics().getAscent()) / 2 - 5;
        
        // Column headers: #, Team, P, W, D, L, GF, GA, GD, Pts
        drawCenteredText(g2d, "#", x, textY, POS_WIDTH);
        x += POS_WIDTH;
        
        drawLeftText(g2d, "Team", x + 10, textY);
        x += TEAM_WIDTH;
        
        String[] headers = {"P", "W", "D", "L", "GF", "GA", "GD", "Pts"};
        for (int i = 0; i < headers.length; i++) {
            int width = (i >= 4) ? STAT_WIDTH_WIDE : STAT_WIDTH;
            drawCenteredText(g2d, headers[i], x, textY, width);
            x += width;
        }
        
        // Bottom border
        g2d.setColor(BORDER_COLOR);
        g2d.drawLine(PADDING, y + HEADER_HEIGHT - 1, IMAGE_WIDTH - PADDING, y + HEADER_HEIGHT - 1);
    }

    private void drawRow(Graphics2D g2d, TeamStanding standing, int position, int y, boolean isEven, boolean isTopThree) {
        // Row background - subtle accent for top 3
        if (isTopThree) {
            Color accentBg = new Color(0x1a, 0x3a, 0x1a); // subtle green tint
            g2d.setColor(isEven ? accentBg : new Color(0x1f, 0x3f, 0x1f));
        } else {
            g2d.setColor(isEven ? ROW_EVEN_COLOR : ROW_ODD_COLOR);
        }
        g2d.fillRect(PADDING, y, IMAGE_WIDTH - PADDING * 2, ROW_HEIGHT);
        
        g2d.setFont(DATA_FONT);
        
        int x = PADDING + 10;
        int textY = y + (ROW_HEIGHT + g2d.getFontMetrics().getAscent()) / 2 - 5;
        
        // Position with color
        g2d.setColor(getPositionColor(position));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
        drawCenteredText(g2d, String.valueOf(position), x, textY, POS_WIDTH);
        x += POS_WIDTH;
        
        // Team name
        g2d.setFont(DATA_FONT);
        g2d.setColor(TEXT_COLOR);
        String teamName = truncateText(standing.getTeamName(), TEAM_WIDTH - 20, g2d.getFontMetrics());
        drawLeftText(g2d, teamName, x + 10, textY);
        x += TEAM_WIDTH;
        
        // Stats: P, W, D, L, GF, GA, GD, Pts
        String[] stats = {
            String.valueOf(standing.getPlayed()),
            String.valueOf(standing.getWon()),
            String.valueOf(standing.getDrawn()),
            String.valueOf(standing.getLost()),
            String.valueOf(standing.getGoalsFor()),
            String.valueOf(standing.getGoalsAgainst()),
            formatGoalDiff(standing.getGoalDifference()),
            String.valueOf(standing.getPoints())
        };
        
        for (int i = 0; i < stats.length; i++) {
            int width = (i >= 4) ? STAT_WIDTH_WIDE : STAT_WIDTH;
            
            // Highlight points column
            if (i == stats.length - 1) {
                g2d.setColor(SCORE_COLOR);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
            } else if (i == 6) { // GD column
                g2d.setColor(standing.getGoalDifference() >= 0 ? SCORE_COLOR : new Color(0xff, 0x66, 0x66));
                g2d.setFont(DATA_FONT);
            } else {
                g2d.setColor(TEXT_COLOR);
                g2d.setFont(DATA_FONT);
            }
            drawCenteredText(g2d, stats[i], x, textY, width);
            x += width;
        }
        
        // Bottom border
        g2d.setColor(BORDER_COLOR);
        g2d.drawLine(PADDING, y + ROW_HEIGHT - 1, IMAGE_WIDTH - PADDING, y + ROW_HEIGHT - 1);
    }

    private void drawFooter(Graphics2D g2d, int y) {
        g2d.setFont(FOOTER_FONT);
        g2d.setColor(new Color(0x66, 0x66, 0x66));
        String timestamp = "Updated: " + LocalDateTime.now().format(TIMESTAMP_FORMAT);
        drawCenteredText(g2d, timestamp, 0, y + 20, IMAGE_WIDTH);
    }

    private String formatGoalDiff(int gd) {
        if (gd > 0) return "+" + gd;
        return String.valueOf(gd);
    }

    private byte[] toBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    public int getTotalPages(int totalItems) {
        return Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
    }

    /**
     * Generate caption with top 3 teams summary.
     */
    public String generateCaption(List<TeamStanding> standings) {
        if (standings.isEmpty()) {
            return "No standings data available.";
        }
        
        StringBuilder caption = new StringBuilder("🏆 Top teams:\n");
        int limit = Math.min(3, standings.size());
        for (int i = 0; i < limit; i++) {
            TeamStanding ts = standings.get(i);
            String medal = switch (i) {
                case 0 -> "🥇";
                case 1 -> "🥈";
                case 2 -> "🥉";
                default -> "";
            };
            caption.append(medal).append(" ").append(ts.getTeamName())
                   .append(" — ").append(ts.getPoints()).append(" pts\n");
        }
        return caption.toString().trim();
    }
}
