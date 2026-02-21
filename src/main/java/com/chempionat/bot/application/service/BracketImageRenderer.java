package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.enums.MatchStage;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 
 * Features:
 * - Displays single elimination bracket tree with all rounds
 * - Supports penalty shootout scores in parentheses (e.g., "2 (5)" for 2 goals + 5 penalties)
 * - Includes third-place match with visual distinction
 * - Dynamic sizing based on team count and text length
 * - Clean connecting lines between rounds
 * - Uses real team names from database
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BracketImageRenderer extends ImageRenderer {

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    // Match box dimensions - dynamically adjusted based on content
    private static final int BASE_MATCH_BOX_WIDTH = 220;
    private static final int MATCH_BOX_HEIGHT = 70;
    private static final int VERTICAL_GAP = 30;
    private static final int HORIZONTAL_GAP = 100;
    private static final int STAGE_HEADER_HEIGHT = 50;
    
    // Third-place match positioning
    private static final int THIRD_PLACE_Y_OFFSET = 40;
    
    // Colors specific to bracket
    private static final Color WINNER_COLOR = new Color(0x00, 0xff, 0x88);
    private static final Color LOSER_COLOR = new Color(0x88, 0x88, 0x88);
    private static final Color TBD_COLOR = new Color(0x66, 0x66, 0x88);
    private static final Color BYE_COLOR = new Color(0x55, 0x88, 0x55);
    private static final Color MATCH_BOX_COLOR = new Color(0x2a, 0x2a, 0x4a);
    private static final Color MATCH_BOX_BORDER = new Color(0x4a, 0x4a, 0x7a);
    private static final Color LINE_COLOR = new Color(0x4a, 0x4a, 0x7a);
    private static final Color PENALTY_COLOR = new Color(0xff, 0xcc, 0x00); // Yellow for penalty indicator
    private static final Color THIRD_PLACE_BG = new Color(0x3a, 0x2a, 0x1a); // Bronze tint for 3rd place

    /**
     * Render tournament bracket to PNG bytes.
     * Dynamically adapts to team count (3, 5, 8, 16, etc.)
     *
     * @param tournamentName name of the tournament
     * @param matches        all matches in bracket order (including third-place if exists)
     * @return PNG image as byte array
     */
    public byte[] render(String tournamentName, List<Match> matches) throws IOException {
        if (matches.isEmpty()) {
            return renderEmptyBracket(tournamentName);
        }

        // Separate main bracket matches from third-place match
        List<Match> mainBracketMatches = matches.stream()
                .filter(m -> m.getStage() != MatchStage.THIRD_PLACE)
                .collect(Collectors.toList());
        
        Optional<Match> thirdPlaceMatch = matches.stream()
                .filter(m -> m.getStage() == MatchStage.THIRD_PLACE || 
                             Boolean.TRUE.equals(m.getIsThirdPlaceMatch()))
                .findFirst();

        // Group main bracket matches by stage for proper layout
        Map<MatchStage, List<Match>> matchesByStage = mainBracketMatches.stream()
                .filter(m -> m.getStage() != null)
                .collect(Collectors.groupingBy(Match::getStage));

        if (matchesByStage.isEmpty()) {
            return renderEmptyBracket(tournamentName);
        }

        // Determine bracket structure
        int maxFirstRoundMatches = getFirstRoundMatchCount(mainBracketMatches);
        int totalRounds = calculateTotalRounds(maxFirstRoundMatches);
        
        // Order stages from first round to final
        List<MatchStage> orderedStages = getOrderedStages(matchesByStage.keySet(), totalRounds);

        // Calculate dynamic match box width based on longest team name
        int matchBoxWidth = calculateMatchBoxWidth(matches);

        // Calculate image dimensions
        int bracketWidth = orderedStages.size() * (matchBoxWidth + HORIZONTAL_GAP) + PADDING * 2;
        int bracketHeight = maxFirstRoundMatches * (MATCH_BOX_HEIGHT + VERTICAL_GAP) + STAGE_HEADER_HEIGHT;
        
        // Add space for third-place match if exists (placed below final)
        int thirdPlaceHeight = thirdPlaceMatch.isPresent() ? MATCH_BOX_HEIGHT + THIRD_PLACE_Y_OFFSET + 50 : 0;
        
        int imageWidth = Math.max(bracketWidth, 900);
        int headerHeight = 90;
        int footerHeight = 50;
        int totalHeight = headerHeight + bracketHeight + thirdPlaceHeight + footerHeight + PADDING * 2;

        BufferedImage image = new BufferedImage(imageWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Setup rendering with high quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Fill background
        g2d.setColor(BACKGROUND_COLOR);
        g2d.fillRect(0, 0, imageWidth, totalHeight);

        int y = PADDING;

        // Draw header
        y = drawHeader(g2d, tournamentName, y, imageWidth);

        // Draw main bracket and get final match position
        BracketDrawResult bracketResult = drawBracketByStageWithFinalPosition(g2d, matchesByStage, orderedStages, y, imageWidth, 
                maxFirstRoundMatches, matchBoxWidth);

        // Draw third-place match below the final match box
        if (thirdPlaceMatch.isPresent() && bracketResult.finalX >= 0) {
            drawThirdPlaceMatchBelowFinal(g2d, thirdPlaceMatch.get(), 
                    bracketResult.finalX, bracketResult.finalY + MATCH_BOX_HEIGHT + THIRD_PLACE_Y_OFFSET, 
                    matchBoxWidth);
        } else if (thirdPlaceMatch.isPresent()) {
            // Fallback: centered if no final position
            drawThirdPlaceMatch(g2d, thirdPlaceMatch.get(), bracketResult.maxY + THIRD_PLACE_Y_OFFSET, 
                    imageWidth, matchBoxWidth);
        }

        // Draw footer
        drawFooter(g2d, totalHeight - 40, imageWidth);

        g2d.dispose();

        log.debug("BRACKET_RENDER: tournamentName={}, matches={}, stages={}, thirdPlace={}", 
                tournamentName, matches.size(), orderedStages.size(), thirdPlaceMatch.isPresent());

        return toBytes(image);
    }

    /**
     * Calculate match box width based on longest team name.
     * Ensures penalty scores fit without overflow.
     */
    private int calculateMatchBoxWidth(List<Match> matches) {
        int maxNameLength = matches.stream()
                .flatMap(m -> {
                    List<String> names = new ArrayList<>();
                    if (m.getHomeTeam() != null) names.add(getTeamDisplayName(m.getHomeTeam()));
                    if (m.getAwayTeam() != null) names.add(getTeamDisplayName(m.getAwayTeam()));
                    return names.stream();
                })
                .mapToInt(String::length)
                .max()
                .orElse(10);
        
        // Account for score area (normal + penalty scores)
        // Format: "TeamName    2 (5)" needs ~70px for scores
        int calculatedWidth = maxNameLength * 8 + 80;
        
        return Math.max(BASE_MATCH_BOX_WIDTH, Math.min(calculatedWidth, 300));
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
        // Order stages from first round to final (excludes THIRD_PLACE)
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
        g2d.fillRoundRect(PADDING, y, imageWidth - PADDING * 2, 70, 15, 15);

        g2d.setFont(TITLE_FONT);
        g2d.setColor(TEXT_COLOR);
        String title = "🏆 " + tournamentName + " — Bracket";
        drawCenteredText(g2d, title, 0, y + 45, imageWidth);

        return y + 80;
    }
    
    /**
     * Helper class to return bracket drawing result with final match position.
     */
    private static class BracketDrawResult {
        int maxY;
        int finalX;
        int finalY;
        
        BracketDrawResult(int maxY, int finalX, int finalY) {
            this.maxY = maxY;
            this.finalX = finalX;
            this.finalY = finalY;
        }
    }

    /**
     * Draw the main bracket stages and return final match position.
     * Third-place match will be placed below the final.
     */
    private BracketDrawResult drawBracketByStageWithFinalPosition(Graphics2D g2d, Map<MatchStage, List<Match>> matchesByStage,
                                    List<MatchStage> orderedStages, int startY, int imageWidth,
                                    int maxFirstRoundMatches, int matchBoxWidth) {
        
        // Store match positions for connecting lines
        Map<Long, Rectangle> matchPositions = new HashMap<>();
        
        int maxY = startY;
        int stageIndex = 0;
        int finalX = -1;
        int finalY = -1;
        
        for (MatchStage stage : orderedStages) {
            List<Match> stageMatches = matchesByStage.get(stage);
            if (stageMatches == null || stageMatches.isEmpty()) continue;
            
            int x = PADDING + stageIndex * (matchBoxWidth + HORIZONTAL_GAP);
            
            // Draw stage header
            g2d.setFont(HEADER_FONT);
            g2d.setColor(HEADER_TEXT_COLOR);
            String stageName = getStageDisplayName(stage);
            drawCenteredText(g2d, stageName, x, startY + 30, matchBoxWidth);
            
            // Calculate vertical spacing for this round
            int matchesInStage = stageMatches.size();
            int slotHeight = MATCH_BOX_HEIGHT + VERTICAL_GAP;
            
            // Calculate spacing to center matches vertically
            int spacingMultiplier = Math.max(1, maxFirstRoundMatches / matchesInStage);
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

                // Draw match box with penalty support
                drawMatchBox(g2d, match, x, matchY, matchBoxWidth, false);
                
                // Store position for connecting lines
                if (match.getId() != null) {
                    matchPositions.put(match.getId(), new Rectangle(x, matchY, matchBoxWidth, MATCH_BOX_HEIGHT));
                }
                
                // Track final match position
                if (stage == MatchStage.FINAL) {
                    finalX = x;
                    finalY = matchY;
                }
                
                maxY = Math.max(maxY, matchY + MATCH_BOX_HEIGHT);
            }
            
            stageIndex++;
        }

        // Draw connecting lines between matches
        drawConnectingLines(g2d, matchesByStage, matchPositions);
        
        return new BracketDrawResult(maxY, finalX, finalY);
    }
    
    /**
     * Draw third-place match directly below the final match box.
     */
    private void drawThirdPlaceMatchBelowFinal(Graphics2D g2d, Match match, int x, int y, int matchBoxWidth) {
        // Draw section label above the match box
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2d.setColor(PENALTY_COLOR);
        String label = "🥉 3-o'rin";
        drawCenteredText(g2d, label, x, y - 5, matchBoxWidth);
        
        // Draw match box at the same X position as final
        drawMatchBox(g2d, match, x, y + 10, matchBoxWidth, true);
        
        log.debug("THIRD_PLACE_BELOW_FINAL: matchId={}, x={}, y={}, teams={}vs{}", 
                match.getId(), x, y,
                match.getHomeTeam() != null ? match.getHomeTeam().getName() : "TBD",
                match.getAwayTeam() != null ? match.getAwayTeam().getName() : "TBD");
    }

    /**
        
        for (MatchStage stage : orderedStages) {
            List<Match> stageMatches = matchesByStage.get(stage);
            if (stageMatches == null || stageMatches.isEmpty()) continue;
            
            int x = PADDING + stageIndex * (matchBoxWidth + HORIZONTAL_GAP);
            
            // Draw stage header
            g2d.setFont(HEADER_FONT);
            g2d.setColor(HEADER_TEXT_COLOR);
            String stageName = getStageDisplayName(stage);
            drawCenteredText(g2d, stageName, x, startY + 30, matchBoxWidth);
            
            // Calculate vertical spacing for this round
            int matchesInStage = stageMatches.size();
            int slotHeight = MATCH_BOX_HEIGHT + VERTICAL_GAP;
            
            // Calculate spacing to center matches vertically
            int spacingMultiplier = Math.max(1, maxFirstRoundMatches / matchesInStage);
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

                // Draw match box with penalty support
                drawMatchBox(g2d, match, x, matchY, matchBoxWidth, false);
                
                // Store position for connecting lines
                if (match.getId() != null) {
                    matchPositions.put(match.getId(), new Rectangle(x, matchY, matchBoxWidth, MATCH_BOX_HEIGHT));
                }
                
                maxY = Math.max(maxY, matchY + MATCH_BOX_HEIGHT);
            }
            
            stageIndex++;
        }

        // Draw connecting lines between matches
        drawConnectingLines(g2d, matchesByStage, matchPositions);
        
        return maxY;
    }

    /**
     * Draw connecting lines between matches in bracket.
     * Uses smooth curves for cleaner appearance.
     */
    private void drawConnectingLines(Graphics2D g2d, Map<MatchStage, List<Match>> matchesByStage,
                                      Map<Long, Rectangle> matchPositions) {
        g2d.setColor(LINE_COLOR);
        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        for (List<Match> stageMatches : matchesByStage.values()) {
            for (Match match : stageMatches) {
                if (match.getNextMatch() != null && match.getId() != null
                        && matchPositions.containsKey(match.getId()) 
                        && matchPositions.containsKey(match.getNextMatch().getId())) {
                    
                    Rectangle from = matchPositions.get(match.getId());
                    Rectangle to = matchPositions.get(match.getNextMatch().getId());
                    
                    drawConnectingLine(g2d, from, to);
                }
            }
        }
        
        g2d.setStroke(new BasicStroke(1));
    }

    /**
     * Draw third-place match with distinct visual styling.
     * Positioned below the main bracket with bronze-colored background.
     */
    private void drawThirdPlaceMatch(Graphics2D g2d, Match match, int startY, int imageWidth, int matchBoxWidth) {
        // Draw section label
        g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2d.setColor(PENALTY_COLOR);
        String label = "🥉 3-o'rin uchun o'yin";
        drawCenteredText(g2d, label, 0, startY, imageWidth);
        
        // Draw match box centered
        int x = (imageWidth - matchBoxWidth) / 2;
        int y = startY + 15;
        
        drawMatchBox(g2d, match, x, y, matchBoxWidth, true);
        
        log.debug("THIRD_PLACE_DRAWN: matchId={}, teams={}vs{}", 
                match.getId(),
                match.getHomeTeam() != null ? match.getHomeTeam().getName() : "TBD",
                match.getAwayTeam() != null ? match.getAwayTeam().getName() : "TBD");
    }

    /**
     * Draw a single match box with team names and scores.
     * 
     * Features:
     * - Penalty scores shown in parentheses: "2 (5)"
     * - Winner highlighted in green, loser in gray
     * - Third-place matches have bronze-tinted background
     * - BYE matches show auto-advance indicator
     *
     * @param g2d Graphics context
     * @param match The match to render
     * @param x X position
     * @param y Y position
     * @param boxWidth Width of match box
     * @param isThirdPlace Whether this is the third-place match
     */
    private void drawMatchBox(Graphics2D g2d, Match match, int x, int y, int boxWidth, boolean isThirdPlace) {
        boolean isBye = Boolean.TRUE.equals(match.getIsBye());
        boolean hasPenalties = Boolean.TRUE.equals(match.getDecidedByPenalties()) 
                && match.getHomePenaltyScore() != null 
                && match.getAwayPenaltyScore() != null;
        
        // Choose background color based on match type
        Color bgColor;
        if (isThirdPlace) {
            bgColor = THIRD_PLACE_BG;
        } else if (isBye) {
            bgColor = new Color(0x25, 0x35, 0x25);
        } else {
            bgColor = MATCH_BOX_COLOR;
        }
        
        // Draw box background
        g2d.setColor(bgColor);
        g2d.fill(new RoundRectangle2D.Float(x, y, boxWidth, MATCH_BOX_HEIGHT, 10, 10));

        // Draw border
        Color borderColor = isThirdPlace ? PENALTY_COLOR : (isBye ? BYE_COLOR : MATCH_BOX_BORDER);
        g2d.setColor(borderColor);
        g2d.draw(new RoundRectangle2D.Float(x, y, boxWidth, MATCH_BOX_HEIGHT, 10, 10));

        // Get team display names
        String homeTeam = getTeamDisplayName(match.getHomeTeam());
        String awayTeam = isBye ? "BYE" : getTeamDisplayName(match.getAwayTeam());
        
        // Determine colors based on match result
        Color homeColor = TEXT_COLOR;
        Color awayColor = isBye ? BYE_COLOR : TEXT_COLOR;
        
        if (match.getHomeScore() != null && match.getAwayScore() != null && !isBye) {
            // Determine winner considering penalties
            Team winner = determineWinner(match);
            if (winner != null) {
                if (match.getHomeTeam() != null && winner.getId().equals(match.getHomeTeam().getId())) {
                    homeColor = WINNER_COLOR;
                    awayColor = LOSER_COLOR;
                } else if (match.getAwayTeam() != null && winner.getId().equals(match.getAwayTeam().getId())) {
                    homeColor = LOSER_COLOR;
                    awayColor = WINNER_COLOR;
                }
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
        
        // Calculate space for scores (normal + penalty)
        int scoreAreaWidth = hasPenalties ? 70 : 40;
        int maxTextWidth = boxWidth - scoreAreaWidth - 20;
        
        // Home team name
        g2d.setColor(homeColor);
        String truncatedHome = truncateText(homeTeam, maxTextWidth, fm);
        g2d.drawString(truncatedHome, textX, homeY);
        
        // Away team name
        g2d.setColor(awayColor);
        String truncatedAway = truncateText(awayTeam, maxTextWidth, fm);
        g2d.drawString(truncatedAway, textX, awayY);
        
        // Draw scores if available (not for BYE)
        if (match.getHomeScore() != null && match.getAwayScore() != null && !isBye) {
            drawScores(g2d, match, x + boxWidth - scoreAreaWidth - 5, homeY, awayY, 
                    homeColor, awayColor, hasPenalties);
        }

        // Draw divider line between teams
        g2d.setColor(MATCH_BOX_BORDER);
        g2d.drawLine(x + 5, y + MATCH_BOX_HEIGHT / 2, x + boxWidth - 5, y + MATCH_BOX_HEIGHT / 2);
        
        // Draw BYE badge if applicable
        if (isBye) {
            g2d.setColor(BYE_COLOR);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2d.drawString("✓ AUTO", x + boxWidth - 55, homeY);
        }
        
        // Draw penalty indicator if match was decided by penalties
        if (hasPenalties) {
            g2d.setColor(PENALTY_COLOR);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 9));
            g2d.drawString("PEN", x + boxWidth - 30, y + 12);
        }
    }

    /**
     * Draw match scores with penalty support.
     * Format: "2 (5)" means 2 goals in normal time, won 5-X in penalties
     */
    private void drawScores(Graphics2D g2d, Match match, int x, int homeY, int awayY,
                           Color homeColor, Color awayColor, boolean hasPenalties) {
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        
        // Home score
        g2d.setColor(homeColor);
        String homeScoreStr = formatScore(match.getHomeScore(), 
                hasPenalties ? match.getHomePenaltyScore() : null);
        g2d.drawString(homeScoreStr, x, homeY);
        
        // Away score
        g2d.setColor(awayColor);
        String awayScoreStr = formatScore(match.getAwayScore(), 
                hasPenalties ? match.getAwayPenaltyScore() : null);
        g2d.drawString(awayScoreStr, x, awayY);
    }

    /**
     * Format score string with optional penalty score.
     * Example: 2 goals + 5 penalties -> "2 (5)"
     */
    private String formatScore(Integer normalScore, Integer penaltyScore) {
        if (normalScore == null) return "";
        if (penaltyScore != null) {
            return String.format("%d (%d)", normalScore, penaltyScore);
        }
        return String.valueOf(normalScore);
    }

    /**
     * Determine winner of a match (considering penalties).
     */
    private Team determineWinner(Match match) {
        if (match.getHomeScore() == null || match.getAwayScore() == null) {
            return null;
        }
        
        // Normal time winner
        if (match.getHomeScore() > match.getAwayScore()) {
            return match.getHomeTeam();
        } else if (match.getAwayScore() > match.getHomeScore()) {
            return match.getAwayTeam();
        }
        
        // Draw - check penalties
        if (Boolean.TRUE.equals(match.getDecidedByPenalties()) 
                && match.getHomePenaltyScore() != null 
                && match.getAwayPenaltyScore() != null) {
            if (match.getHomePenaltyScore() > match.getAwayPenaltyScore()) {
                return match.getHomeTeam();
            } else if (match.getAwayPenaltyScore() > match.getHomePenaltyScore()) {
                return match.getAwayTeam();
            }
        }
        
        return null;
    }

    private void drawConnectingLine(Graphics2D g2d, Rectangle from, Rectangle to) {
        int fromX = from.x + from.width;
        int fromY = from.y + from.height / 2;
        int toX = to.x;
        int toY = to.y + to.height / 2;
        int midX = fromX + (toX - fromX) / 2;

        // Draw connector with three segments (horizontal-vertical-horizontal)
        g2d.drawLine(fromX, fromY, midX, fromY);
        g2d.drawLine(midX, fromY, midX, toY);
        g2d.drawLine(midX, toY, toX, toY);
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
     * Includes penalty information if any matches were decided by penalties.
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
        
        long penaltyMatches = matches.stream()
                .filter(m -> Boolean.TRUE.equals(m.getDecidedByPenalties()))
                .count();
        
        // Find current stage
        String currentStage = matches.stream()
                .filter(m -> m.getHomeScore() == null || m.getAwayScore() == null)
                .filter(m -> !Boolean.TRUE.equals(m.getIsBye()))
                .filter(m -> m.getStage() != MatchStage.THIRD_PLACE)
                .findFirst()
                .map(m -> getStageDisplayName(m.getStage()))
                .orElse("Tugallangan");

        StringBuilder caption = new StringBuilder();
        caption.append(String.format("🏆 %s\n📍 Joriy bosqich: %s\n⚽ %d/%d o'yin tugadi", 
                tournamentName, currentStage, completed, total));
        
        if (penaltyMatches > 0) {
            caption.append(String.format("\n🎯 %d ta o'yin penaltida hal bo'ldi", penaltyMatches));
        }
        
        // Check for third-place match
        Optional<Match> thirdPlace = matches.stream()
                .filter(m -> m.getStage() == MatchStage.THIRD_PLACE || 
                             Boolean.TRUE.equals(m.getIsThirdPlaceMatch()))
                .findFirst();
        
        if (thirdPlace.isPresent()) {
            Match tp = thirdPlace.get();
            if (tp.getHomeScore() != null && tp.getAwayScore() != null) {
                Team winner = determineWinner(tp);
                if (winner != null) {
                    caption.append(String.format("\n🥉 3-o'rin: %s", winner.getName()));
                }
            }
        }
        
        return caption.toString();
    }
}
