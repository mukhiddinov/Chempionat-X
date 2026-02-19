package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.enums.MatchStage;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders tournament bracket as a dark-themed PNG image.
 * Displays single elimination bracket tree with all rounds.
 * Uses real team names, handles BYE matches, and adapts to any bracket size.
 */
@Component
@RequiredArgsConstructor
public class BracketImageRenderer extends ImageRenderer {

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    private static final int MATCH_BOX_WIDTH = 200;
    private static final int MATCH_BOX_HEIGHT = 70;
    private static final int VERTICAL_GAP = 25;
    private static final int HORIZONTAL_GAP = 80;
    private static final int STAGE_HEADER_HEIGHT = 45;
    
    // Colors specific to bracket
    private static final Color WINNER_COLOR = new Color(0x00, 0xff, 0x88);
    private static final Color LOSER_COLOR = new Color(0x88, 0x88, 0x88);
    private static final Color TBD_COLOR = new Color(0x66, 0x66, 0x88);
    private static final Color BYE_COLOR = new Color(0x55, 0x88, 0x55);
    private static final Color MATCH_BOX_COLOR = new Color(0x2a, 0x2a, 0x4a);
    private static final Color MATCH_BOX_BORDER = new Color(0x4a, 0x4a, 0x7a);
    private static final Color LINE_COLOR = new Color(0x4a, 0x4a, 0x7a);

    /**
     * Render tournament bracket to PNG bytes.
     * Dynamically adapts to team count (3, 5, 8, 16, etc.)
     *
     * @param tournamentName name of the tournament
     * @param matches        all matches in bracket order
     * @return PNG image as byte array
     */
    public byte[] render(String tournamentName, List<Match> matches) throws IOException {
        if (matches.isEmpty()) {
            return renderEmptyBracket(tournamentName);
        }

        // Group matches by stage for proper bracket layout
        Map<MatchStage, List<Match>> matchesByStage = matches.stream()
                .filter(m -> m.getStage() != null)
                .collect(Collectors.groupingBy(Match::getStage));

        if (matchesByStage.isEmpty()) {
            return renderEmptyBracket(tournamentName);
        }

        // Determine bracket structure
        int maxFirstRoundMatches = getFirstRoundMatchCount(matches);
        int totalRounds = calculateTotalRounds(maxFirstRoundMatches);
        
        // Order stages from first round to final
        List<MatchStage> orderedStages = getOrderedStages(matchesByStage.keySet(), totalRounds);

        // Calculate image dimensions
        int bracketWidth = orderedStages.size() * (MATCH_BOX_WIDTH + HORIZONTAL_GAP) + PADDING * 2;
        int bracketHeight = maxFirstRoundMatches * (MATCH_BOX_HEIGHT + VERTICAL_GAP) + STAGE_HEADER_HEIGHT;
        
        int imageWidth = Math.max(bracketWidth, 800);
        int headerHeight = 80;
        int footerHeight = 50;
        int totalHeight = headerHeight + bracketHeight + footerHeight + PADDING * 2;

        BufferedImage image = new BufferedImage(imageWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Setup rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Fill background
        g2d.setColor(BACKGROUND_COLOR);
        g2d.fillRect(0, 0, imageWidth, totalHeight);

        int y = PADDING;

        // Draw header
        y = drawHeader(g2d, tournamentName, y, imageWidth);

        // Draw bracket
        drawBracketByStage(g2d, matchesByStage, orderedStages, y, imageWidth, maxFirstRoundMatches);

        // Draw footer
        drawFooter(g2d, totalHeight - 40, imageWidth);

        g2d.dispose();

        return toBytes(image);
    }

    private int getFirstRoundMatchCount(List<Match> matches) {
        // Find the first round (lowest round number or earliest stage)
        int minRound = matches.stream()
                .filter(m -> m.getRound() != null)
                .mapToInt(Match::getRound)
                .min()
                .orElse(1);
        
        return (int) matches.stream()
                .filter(m -> m.getRound() != null && m.getRound() == minRound)
                .count();
    }

    private int calculateTotalRounds(int firstRoundMatches) {
        if (firstRoundMatches <= 1) return 1;
        return (int) Math.ceil(Math.log(firstRoundMatches * 2) / Math.log(2));
    }

    private List<MatchStage> getOrderedStages(Set<MatchStage> stages, int totalRounds) {
        // Order stages from first round to final
        List<MatchStage> allStages = List.of(
                MatchStage.ROUND_OF_64,
                MatchStage.ROUND_OF_32,
                MatchStage.ROUND_OF_16,
                MatchStage.QUARTER_FINAL,
                MatchStage.SEMI_FINAL,
                MatchStage.FINAL
        );
        
        return allStages.stream()
                .filter(stages::contains)
                .collect(Collectors.toList());
    }

    private int drawHeader(Graphics2D g2d, String tournamentName, int y, int imageWidth) {
        g2d.setColor(HEADER_BG_COLOR);
        g2d.fillRoundRect(PADDING, y, imageWidth - PADDING * 2, 60, 15, 15);

        g2d.setFont(TITLE_FONT);
        g2d.setColor(TEXT_COLOR);
        String title = "🏆 " + tournamentName + " — Bracket";
        drawCenteredText(g2d, title, 0, y + 40, imageWidth);

        return y + 70;
    }

    private void drawBracketByStage(Graphics2D g2d, Map<MatchStage, List<Match>> matchesByStage,
                                     List<MatchStage> orderedStages, int startY, int imageWidth,
                                     int maxFirstRoundMatches) {
        
        // Store match positions for connecting lines
        Map<Long, Rectangle> matchPositions = new HashMap<>();
        
        int stageIndex = 0;
        for (MatchStage stage : orderedStages) {
            List<Match> stageMatches = matchesByStage.get(stage);
            if (stageMatches == null || stageMatches.isEmpty()) continue;
            
            int x = PADDING + stageIndex * (MATCH_BOX_WIDTH + HORIZONTAL_GAP);
            
            // Draw stage header
            g2d.setFont(HEADER_FONT);
            g2d.setColor(HEADER_TEXT_COLOR);
            String stageName = getStageDisplayName(stage);
            drawCenteredText(g2d, stageName, x, startY + 25, MATCH_BOX_WIDTH);
            
            // Calculate vertical spacing for this round
            int matchesInStage = stageMatches.size();
            int slotHeight = MATCH_BOX_HEIGHT + VERTICAL_GAP;
            
            // Calculate spacing to center matches vertically
            int spacingMultiplier = maxFirstRoundMatches / matchesInStage;
            int baseY = startY + STAGE_HEADER_HEIGHT;
            
            // Sort matches by bracket position if available
            stageMatches.sort(Comparator.comparing(m -> 
                    m.getBracketPosition() != null ? m.getBracketPosition() : 0));
            
            for (int matchIdx = 0; matchIdx < stageMatches.size(); matchIdx++) {
                Match match = stageMatches.get(matchIdx);
                
                // Calculate Y position for this match (centered within allocated space)
                int slotStart = matchIdx * spacingMultiplier;
                int slotEnd = slotStart + spacingMultiplier - 1;
                int centerSlot = (slotStart + slotEnd) / 2;
                int matchY = baseY + centerSlot * slotHeight;

                // Draw match box
                drawMatchBox(g2d, match, x, matchY);
                
                // Store position for connecting lines
                if (match.getId() != null) {
                    matchPositions.put(match.getId(), new Rectangle(x, matchY, MATCH_BOX_WIDTH, MATCH_BOX_HEIGHT));
                }
            }
            
            stageIndex++;
        }

        // Draw connecting lines
        for (List<Match> stageMatches : matchesByStage.values()) {
            for (Match match : stageMatches) {
                if (match.getNextMatch() != null && match.getId() != null
                        && matchPositions.containsKey(match.getId()) 
                        && matchPositions.containsKey(match.getNextMatch().getId())) {
                    drawConnectingLine(g2d, matchPositions.get(match.getId()), 
                            matchPositions.get(match.getNextMatch().getId()));
                }
            }
        }
    }

    private void drawMatchBox(Graphics2D g2d, Match match, int x, int y) {
        boolean isBye = Boolean.TRUE.equals(match.getIsBye());
        
        // Draw box background
        g2d.setColor(isBye ? new Color(0x25, 0x35, 0x25) : MATCH_BOX_COLOR);
        g2d.fill(new RoundRectangle2D.Float(x, y, MATCH_BOX_WIDTH, MATCH_BOX_HEIGHT, 10, 10));

        // Draw border
        g2d.setColor(isBye ? BYE_COLOR : MATCH_BOX_BORDER);
        g2d.draw(new RoundRectangle2D.Float(x, y, MATCH_BOX_WIDTH, MATCH_BOX_HEIGHT, 10, 10));

        // Get team display names - USE REAL NAMES
        String homeTeam = getTeamDisplayName(match.getHomeTeam());
        String awayTeam = isBye ? "BYE" : getTeamDisplayName(match.getAwayTeam());
        
        // Determine colors based on match result
        Color homeColor = TEXT_COLOR;
        Color awayColor = isBye ? BYE_COLOR : TEXT_COLOR;
        
        if (match.getHomeScore() != null && match.getAwayScore() != null && !isBye) {
            if (match.getHomeScore() > match.getAwayScore()) {
                homeColor = WINNER_COLOR;
                awayColor = LOSER_COLOR;
            } else if (match.getAwayScore() > match.getHomeScore()) {
                homeColor = LOSER_COLOR;
                awayColor = WINNER_COLOR;
            }
        } else if (match.getHomeTeam() == null) {
            homeColor = TBD_COLOR;
        }
        
        if (match.getAwayTeam() == null && !isBye) {
            awayColor = TBD_COLOR;
        }

        // Draw team names
        g2d.setFont(DATA_FONT);
        FontMetrics fm = g2d.getFontMetrics();
        
        int textX = x + 12;
        int homeY = y + 26;
        int awayY = y + 52;
        
        // Home team (real name)
        g2d.setColor(homeColor);
        String truncatedHome = truncateText(homeTeam, MATCH_BOX_WIDTH - 70, fm);
        g2d.drawString(truncatedHome, textX, homeY);
        
        // Away team (real name or BYE)
        g2d.setColor(awayColor);
        String truncatedAway = truncateText(awayTeam, MATCH_BOX_WIDTH - 70, fm);
        g2d.drawString(truncatedAway, textX, awayY);
        
        // Draw scores if available (not for BYE)
        if (match.getHomeScore() != null && match.getAwayScore() != null && !isBye) {
            g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
            int scoreX = x + MATCH_BOX_WIDTH - 35;
            
            g2d.setColor(homeColor);
            g2d.drawString(String.valueOf(match.getHomeScore()), scoreX, homeY);
            
            g2d.setColor(awayColor);
            g2d.drawString(String.valueOf(match.getAwayScore()), scoreX, awayY);
        }

        // Draw divider line between teams
        g2d.setColor(MATCH_BOX_BORDER);
        g2d.drawLine(x + 5, y + MATCH_BOX_HEIGHT / 2, x + MATCH_BOX_WIDTH - 5, y + MATCH_BOX_HEIGHT / 2);
        
        // Draw BYE badge if applicable
        if (isBye) {
            g2d.setColor(BYE_COLOR);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2d.drawString("✓ AUTO", x + MATCH_BOX_WIDTH - 55, homeY);
        }
    }

    private void drawConnectingLine(Graphics2D g2d, Rectangle from, Rectangle to) {
        g2d.setColor(LINE_COLOR);
        g2d.setStroke(new BasicStroke(2));

        int fromX = from.x + from.width;
        int fromY = from.y + from.height / 2;
        int toX = to.x;
        int toY = to.y + to.height / 2;
        int midX = fromX + (toX - fromX) / 2;

        // Draw connector with three segments
        g2d.drawLine(fromX, fromY, midX, fromY);
        g2d.drawLine(midX, fromY, midX, toY);
        g2d.drawLine(midX, toY, toX, toY);
        
        g2d.setStroke(new BasicStroke(1));
    }

    private void drawFooter(Graphics2D g2d, int y, int imageWidth) {
        g2d.setFont(FOOTER_FONT);
        g2d.setColor(new Color(0x66, 0x66, 0x66));
        String timestamp = "Generated by Chempionat-X Bot";
        drawCenteredText(g2d, timestamp, 0, y, imageWidth);
    }

    private byte[] renderEmptyBracket(String tournamentName) throws IOException {
        BufferedImage image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(BACKGROUND_COLOR);
        g2d.fillRect(0, 0, 600, 200);

        g2d.setFont(TITLE_FONT);
        g2d.setColor(TEXT_COLOR);
        drawCenteredText(g2d, tournamentName, 0, 80, 600);

        g2d.setFont(DATA_FONT);
        g2d.setColor(TBD_COLOR);
        drawCenteredText(g2d, "Bracket not available yet", 0, 120, 600);

        g2d.dispose();
        return toBytes(image);
    }

    /**
     * Get display name for a team - uses REAL team name, not placeholder.
     */
    private String getTeamDisplayName(Team team) {
        if (team == null) return "TBD";
        
        // Priority: Team name > Username > First name > "TBD"
        if (team.getName() != null && !team.getName().isBlank()) {
            return team.getName();
        }
        if (team.getUser() != null) {
            if (team.getUser().getUsername() != null && !team.getUser().getUsername().isBlank()) {
                return "@" + team.getUser().getUsername();
            }
            if (team.getUser().getFirstName() != null && !team.getUser().getFirstName().isBlank()) {
                return team.getUser().getFirstName();
            }
        }
        return "TBD";
    }

    private String getStageDisplayName(MatchStage stage) {
        if (stage == null) return "";
        return switch (stage) {
            case ROUND_OF_64 -> "1/32";
            case ROUND_OF_32 -> "1/16";
            case ROUND_OF_16 -> "1/8";
            case QUARTER_FINAL -> "Chorak Final";
            case SEMI_FINAL -> "Yarim Final";
            case THIRD_PLACE -> "3-o'rin";
            case FINAL -> "FINAL";
            default -> stage.name();
        };
    }

    private byte[] toBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Generate caption for bracket image with real match info.
     */
    public String generateCaption(String tournamentName, List<Match> matches) {
        if (matches.isEmpty()) {
            return "🏆 " + tournamentName;
        }

        long completed = matches.stream()
                .filter(m -> m.getHomeScore() != null && m.getAwayScore() != null)
                .filter(m -> !Boolean.TRUE.equals(m.getIsBye()))
                .count();
        
        long total = matches.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsBye()))
                .count();
        
        // Find current stage
        String currentStage = matches.stream()
                .filter(m -> m.getHomeScore() == null || m.getAwayScore() == null)
                .filter(m -> !Boolean.TRUE.equals(m.getIsBye()))
                .findFirst()
                .map(m -> getStageDisplayName(m.getStage()))
                .orElse("Tugallangan");

        return String.format("🏆 %s\n📍 Joriy bosqich: %s\n⚽ %d/%d o'yin tugadi", 
                tournamentName, currentStage, completed, total);
    }
}
