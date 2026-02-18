package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.model.Match;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders tournament fixtures/rounds as a dark-themed PNG image.
 * Format: "HH:mm  HomeTeam  vs  AwayTeam  (Score/Status)"
 */
@Component
public class FixturesImageRenderer extends ImageRenderer {

    private static final int DEFAULT_ITEMS_PER_PAGE = 12;
    private int itemsPerPage = DEFAULT_ITEMS_PER_PAGE;
    
    private static final int FIXTURE_HEIGHT = 65;
    private static final int ROUND_HEADER_HEIGHT = 45;
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    
    // Status colors
    private static final Color LIVE_COLOR = new Color(0xff, 0x00, 0x00);
    private static final Color FT_COLOR = new Color(0x00, 0xff, 0x88);
    private static final Color SCHEDULED_COLOR = new Color(0x88, 0x88, 0x88);

    public void setItemsPerPage(int itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }

    /**
     * Render fixtures grouped by round to PNG bytes.
     *
     * @param tournamentName name of the tournament
     * @param matches        list of all matches
     * @param page           current page (0-indexed)
     * @return PNG image as byte array
     */
    public byte[] render(String tournamentName, List<Match> matches, int page) throws IOException {
        // Group matches by round
        Map<Integer, List<Match>> matchesByRound = matches.stream()
                .filter(m -> m.getRound() != null)
                .sorted(Comparator.comparing(Match::getRound)
                        .thenComparing(m -> m.getScheduledTime() != null ? m.getScheduledTime() : LocalDateTime.MAX))
                .collect(Collectors.groupingBy(Match::getRound, LinkedHashMap::new, Collectors.toList()));

        // Flatten to list for pagination
        List<Object> items = new ArrayList<>();
        for (Map.Entry<Integer, List<Match>> entry : matchesByRound.entrySet()) {
            items.add("ROUND:" + entry.getKey());
            items.addAll(entry.getValue());
        }

        int totalPages = calculateTotalPages(items);
        page = Math.max(0, Math.min(page, totalPages - 1));

        List<Object> pageItems = getPageItems(items, page);

        // Calculate image height
        int headerCardHeight = 90;
        int contentHeight = calculateContentHeight(pageItems);
        int footerHeight = 50;
        int totalHeight = headerCardHeight + contentHeight + footerHeight + PADDING * 2;

        BufferedImage image = createImage(totalHeight);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int y = PADDING;

        // Draw header card
        y = drawHeaderCard(g2d, tournamentName, page + 1, totalPages, y);

        // Draw content
        y = drawContent(g2d, pageItems, y);

        // Draw footer
        y += 10;
        drawFooter(g2d, y);

        g2d.dispose();

        return toBytes(image);
    }

    /**
     * Render a specific round to PNG.
     */
    public byte[] renderRound(String tournamentName, int roundNumber, List<Match> roundMatches, int page) throws IOException {
        int totalPages = Math.max(1, (int) Math.ceil((double) roundMatches.size() / itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));
        
        int startIdx = page * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, roundMatches.size());
        List<Match> pageMatches = roundMatches.subList(startIdx, endIdx);

        // Calculate image height
        int headerCardHeight = 90;
        int contentHeight = ROUND_HEADER_HEIGHT + (pageMatches.size() * FIXTURE_HEIGHT);
        int footerHeight = 50;
        int totalHeight = headerCardHeight + contentHeight + footerHeight + PADDING * 2;

        BufferedImage image = createImage(totalHeight);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int y = PADDING;

        // Header card with round number
        y = drawHeaderCardForRound(g2d, tournamentName, roundNumber, page + 1, totalPages, y);

        // Draw round header
        y = drawRoundHeader(g2d, roundNumber, y);

        // Draw fixtures
        for (int i = 0; i < pageMatches.size(); i++) {
            Match match = pageMatches.get(i);
            drawFixture(g2d, match, y, i % 2 == 0);
            y += FIXTURE_HEIGHT;
        }

        // Footer
        y += 10;
        drawFooter(g2d, y);

        g2d.dispose();

        return toBytes(image);
    }

    private int drawHeaderCard(Graphics2D g2d, String tournamentName, int currentPage, int totalPages, int y) {
        g2d.setColor(HEADER_BG_COLOR);
        g2d.fillRoundRect(PADDING, y, IMAGE_WIDTH - PADDING * 2, 70, 15, 15);

        g2d.setFont(TITLE_FONT);
        g2d.setColor(TEXT_COLOR);
        String title = tournamentName + " — Fixtures";
        drawLeftText(g2d, title, PADDING + 20, y + 42);

        g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2d.setColor(HEADER_TEXT_COLOR);
        String pageText = "Page " + currentPage + "/" + totalPages;
        drawRightText(g2d, pageText, PADDING, y + 42, IMAGE_WIDTH - PADDING * 2 - 20);

        return y + 80;
    }

    private int drawHeaderCardForRound(Graphics2D g2d, String tournamentName, int round, int currentPage, int totalPages, int y) {
        g2d.setColor(HEADER_BG_COLOR);
        g2d.fillRoundRect(PADDING, y, IMAGE_WIDTH - PADDING * 2, 70, 15, 15);

        g2d.setFont(TITLE_FONT);
        g2d.setColor(TEXT_COLOR);
        String title = tournamentName + " — Round " + round;
        drawLeftText(g2d, title, PADDING + 20, y + 42);

        g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2d.setColor(HEADER_TEXT_COLOR);
        String pageText = "Page " + currentPage + "/" + totalPages;
        drawRightText(g2d, pageText, PADDING, y + 42, IMAGE_WIDTH - PADDING * 2 - 20);

        return y + 80;
    }

    /**
     * Get items for a specific page, ensuring round headers are properly placed.
     * If a round is split across pages, the round header is repeated on each page.
     */
    private List<Object> getPageItems(List<Object> items, int page) {
        // First, organize items into rounds
        List<RoundGroup> roundGroups = new ArrayList<>();
        RoundGroup currentGroup = null;
        
        for (Object item : items) {
            if (item instanceof String s && s.startsWith("ROUND:")) {
                int roundNum = Integer.parseInt(s.substring(6));
                currentGroup = new RoundGroup(roundNum);
                roundGroups.add(currentGroup);
            } else if (item instanceof Match && currentGroup != null) {
                currentGroup.matches.add((Match) item);
            }
        }
        
        // Now paginate: each page has itemsPerPage matches maximum
        // Round headers are added when a round's matches appear on the page
        List<Object> result = new ArrayList<>();
        int matchesSeen = 0;
        int pageStart = page * itemsPerPage;
        int pageEnd = pageStart + itemsPerPage;
        
        for (RoundGroup group : roundGroups) {
            boolean roundHeaderAddedForThisPage = false;
            
            for (Match match : group.matches) {
                if (matchesSeen >= pageStart && matchesSeen < pageEnd) {
                    // Add round header if this is the first match of this round on this page
                    if (!roundHeaderAddedForThisPage) {
                        result.add("ROUND:" + group.roundNumber);
                        roundHeaderAddedForThisPage = true;
                    }
                    result.add(match);
                }
                matchesSeen++;
                
                // Stop if we've filled this page
                if (matchesSeen >= pageEnd) {
                    break;
                }
            }
            
            // Stop iterating rounds if we've filled this page
            if (matchesSeen >= pageEnd) {
                break;
            }
        }
        
        return result;
    }
    
    /**
     * Helper class to group matches by round.
     */
    private static class RoundGroup {
        final int roundNumber;
        final List<Match> matches = new ArrayList<>();
        
        RoundGroup(int roundNumber) {
            this.roundNumber = roundNumber;
        }
    }

    private int calculateTotalPages(List<Object> items) {
        long matchCount = items.stream().filter(i -> i instanceof Match).count();
        return Math.max(1, (int) Math.ceil((double) matchCount / itemsPerPage));
    }

    private int calculateContentHeight(List<Object> items) {
        int height = 0;
        for (Object item : items) {
            if (item instanceof String) {
                height += ROUND_HEADER_HEIGHT;
            } else if (item instanceof Match) {
                height += FIXTURE_HEIGHT;
            }
        }
        return Math.max(height, 100);
    }

    private int drawContent(Graphics2D g2d, List<Object> items, int y) {
        int matchIdx = 0;
        for (Object item : items) {
            if (item instanceof String s && s.startsWith("ROUND:")) {
                int round = Integer.parseInt(s.substring(6));
                y = drawRoundHeader(g2d, round, y);
            } else if (item instanceof Match match) {
                drawFixture(g2d, match, y, matchIdx % 2 == 0);
                y += FIXTURE_HEIGHT;
                matchIdx++;
            }
        }
        return y;
    }

    private int drawRoundHeader(Graphics2D g2d, int round, int y) {
        g2d.setColor(new Color(0x0f, 0x0f, 0x23));
        g2d.fillRect(PADDING, y, IMAGE_WIDTH - PADDING * 2, ROUND_HEADER_HEIGHT);

        g2d.setFont(HEADER_FONT);
        g2d.setColor(HEADER_TEXT_COLOR);
        int textY = y + (ROUND_HEADER_HEIGHT + g2d.getFontMetrics().getAscent()) / 2 - 5;
        drawCenteredText(g2d, "⚽ Round " + round, 0, textY, IMAGE_WIDTH);

        g2d.setColor(BORDER_COLOR);
        g2d.drawLine(PADDING, y + ROUND_HEADER_HEIGHT - 1, IMAGE_WIDTH - PADDING, y + ROUND_HEADER_HEIGHT - 1);

        return y + ROUND_HEADER_HEIGHT;
    }

    private void drawFixture(Graphics2D g2d, Match match, int y, boolean isEven) {
        // Row background
        g2d.setColor(isEven ? ROW_EVEN_COLOR : ROW_ODD_COLOR);
        g2d.fillRect(PADDING, y, IMAGE_WIDTH - PADDING * 2, FIXTURE_HEIGHT);

        // Handle bye matches
        if (match.getIsBye() != null && match.getIsBye()) {
            g2d.setFont(DATA_FONT);
            g2d.setColor(new Color(0x88, 0x88, 0x88));
            int textY = y + (FIXTURE_HEIGHT + g2d.getFontMetrics().getAscent()) / 2 - 5;
            String byeText = "🛌 " + getTeamName(match.getHomeTeam()) + " — BYE";
            drawCenteredText(g2d, byeText, 0, textY, IMAGE_WIDTH);
        } else {
            drawRegularFixture(g2d, match, y);
        }

        // Bottom border
        g2d.setColor(BORDER_COLOR);
        g2d.drawLine(PADDING, y + FIXTURE_HEIGHT - 1, IMAGE_WIDTH - PADDING, y + FIXTURE_HEIGHT - 1);
    }

    private void drawRegularFixture(Graphics2D g2d, Match match, int y) {
        FontMetrics fm = g2d.getFontMetrics(DATA_FONT);
        int textY = y + (FIXTURE_HEIGHT + fm.getAscent()) / 2 - 5;
        
        // Layout constants - calculated dynamically
        int rowStartX = PADDING;
        int rowEndX = IMAGE_WIDTH - PADDING;
        int rowWidth = rowEndX - rowStartX;
        
        // Time column width
        int timeWidth = 70;
        int timeX = rowStartX + 15;
        
        // Score column - centered in the row (excluding time column)
        int contentStartX = rowStartX + timeWidth;
        int contentWidth = rowWidth - timeWidth - 80; // 80 for status badge area
        int scoreCenterX = contentStartX + contentWidth / 2;
        
        // Calculate score width dynamically
        String scoreOrVs;
        Color scoreColor;
        MatchStatus status = getMatchStatus(match);
        
        if (match.getHomeScore() != null && match.getAwayScore() != null) {
            scoreOrVs = match.getHomeScore() + " – " + match.getAwayScore();
            scoreColor = FT_COLOR;
        } else {
            scoreOrVs = "—";
            scoreColor = SCHEDULED_COLOR;
        }
        
        g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
        FontMetrics scoreFm = g2d.getFontMetrics();
        int scoreWidth = scoreFm.stringWidth(scoreOrVs);
        int scoreMargin = 25; // margin between score and team names
        
        // Team areas - dynamically calculated based on score position
        int homeTeamEndX = scoreCenterX - scoreWidth / 2 - scoreMargin;
        int awayTeamStartX = scoreCenterX + scoreWidth / 2 + scoreMargin;
        int homeTeamWidth = homeTeamEndX - contentStartX;
        int awayTeamWidth = contentStartX + contentWidth - awayTeamStartX;
        
        // Draw time
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2d.setColor(new Color(0x88, 0x88, 0xaa));
        String time = match.getScheduledTime() != null ? match.getScheduledTime().format(TIME_FORMAT) : "--:--";
        drawLeftText(g2d, time, timeX, textY);
        
        // Draw home team (right-aligned, ending before score)
        g2d.setFont(DATA_FONT);
        g2d.setColor(TEXT_COLOR);
        String homeTeam = truncateText(getTeamName(match.getHomeTeam()), homeTeamWidth - 10, g2d.getFontMetrics());
        int homeTeamTextWidth = g2d.getFontMetrics().stringWidth(homeTeam);
        int homeTeamX = homeTeamEndX - homeTeamTextWidth;
        g2d.drawString(homeTeam, homeTeamX, textY);
        
        // Draw score (centered)
        g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2d.setColor(scoreColor);
        int scoreX = scoreCenterX - scoreWidth / 2;
        g2d.drawString(scoreOrVs, scoreX, textY);
        
        // Draw away team (left-aligned, starting after score)
        g2d.setFont(DATA_FONT);
        g2d.setColor(TEXT_COLOR);
        String awayTeam = truncateText(getTeamName(match.getAwayTeam()), awayTeamWidth - 10, g2d.getFontMetrics());
        g2d.drawString(awayTeam, awayTeamStartX, textY);
        
        // Status badge (at the end)
        int statusX = rowEndX - 70;
        if (status == MatchStatus.LIVE) {
            drawLiveBadge(g2d, statusX, y + (FIXTURE_HEIGHT - 20) / 2);
        } else if (status == MatchStatus.FT) {
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2d.setColor(FT_COLOR);
            drawLeftText(g2d, "FT", statusX, textY);
        }
    }

    private void drawLiveBadge(Graphics2D g2d, int x, int y) {
        // Red pill-shaped badge with "LIVE" text
        g2d.setColor(LIVE_COLOR);
        g2d.fillRoundRect(x, y, 50, 20, 10, 10);
        
        g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2d.setColor(Color.WHITE);
        drawCenteredText(g2d, "LIVE", x, y + 15, 50);
    }

    private void drawFooter(Graphics2D g2d, int y) {
        g2d.setFont(FOOTER_FONT);
        g2d.setColor(new Color(0x66, 0x66, 0x66));
        String timestamp = "Updated: " + LocalDateTime.now().format(TIMESTAMP_FORMAT);
        drawCenteredText(g2d, timestamp, 0, y + 20, IMAGE_WIDTH);
    }

    private String getTeamName(com.chempionat.bot.domain.model.Team team) {
        if (team == null) return "TBD";
        if (team.getUser() != null) {
            if (team.getUser().getUsername() != null) {
                return "@" + team.getUser().getUsername();
            }
            if (team.getUser().getFirstName() != null) {
                return team.getUser().getFirstName();
            }
        }
        return team.getName() != null ? team.getName() : "TBD";
    }

    private MatchStatus getMatchStatus(Match match) {
        if (match.getHomeScore() != null && match.getAwayScore() != null) {
            // Check if result is approved (FT) or pending
            if (match.getResult() != null && 
                match.getResult().getIsApproved() != null && 
                match.getResult().getIsApproved()) {
                return MatchStatus.FT;
            }
            // Has score but not approved - could be live or pending
            return MatchStatus.PENDING;
        }
        return MatchStatus.SCHEDULED;
    }

    private byte[] toBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    public int getTotalPages(List<Match> matches) {
        return Math.max(1, (int) Math.ceil((double) matches.size() / itemsPerPage));
    }

    /**
     * Generate caption with match count summary (excluding bye matches).
     */
    public String generateCaption(List<Match> matches, Integer roundNumber) {
        // Filter out bye matches for accurate counts
        List<Match> realMatches = matches.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsBye()))
                .filter(m -> m.getHomeTeam() != null && m.getAwayTeam() != null)
                .filter(m -> !m.getHomeTeam().getId().equals(m.getAwayTeam().getId()))
                .toList();
        
        long completed = realMatches.stream()
                .filter(m -> m.getHomeScore() != null && m.getAwayScore() != null)
                .count();
        long total = realMatches.size();
        
        StringBuilder caption = new StringBuilder();
        if (roundNumber != null) {
            caption.append("📅 Round ").append(roundNumber).append("\n");
        }
        caption.append("⚽ ").append(total).append(" matches");
        if (completed > 0) {
            caption.append(" (").append(completed).append(" completed)");
        }
        return caption.toString();
    }

    /**
     * Match status enum.
     */
    private enum MatchStatus {
        SCHEDULED,
        LIVE,
        PENDING,
        FT
    }
}
