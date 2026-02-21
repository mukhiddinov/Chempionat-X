package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.infrastructure.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for generating and caching tournament images.
 * Images are cached by tournament ID, page, and last update time.
 * 
 * IMPORTANT: Cache invalidation relies on tournament.updatedAt being updated
 * whenever standings change (result approval, match completion, etc.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCacheService {

    private final StandingsImageRenderer standingsRenderer;
    private final FixturesImageRenderer fixturesRenderer;

    /**
     * Get standings image from cache or generate new one.
     * Cache key includes tournament ID, page, and full updatedAt timestamp (epoch millis).
     * This ensures any change to tournament.updatedAt invalidates the cache.
     */
    @Cacheable(value = CacheConfig.IMAGE_CACHE, 
               key = "'standings:' + #tournament.id + ':' + #page + ':' + (#tournament.updatedAt != null ? #tournament.updatedAt.toEpochSecond(java.time.ZoneOffset.UTC) : 0)")
    public byte[] getStandingsImage(Tournament tournament, List<TeamStanding> standings, int page) {
        log.info("STANDINGS_IMAGE_GENERATE: tournamentId={}, page={}, updatedAt={}, teamsCount={}", 
                tournament.getId(), page, tournament.getUpdatedAt(), standings.size());
        
        // Log standings snapshot for debugging
        if (!standings.isEmpty()) {
            log.debug("STANDINGS_SNAPSHOT: tournamentId={}, topTeam={}, topPoints={}", 
                    tournament.getId(), 
                    standings.get(0).getTeamName(), 
                    standings.get(0).getPoints());
        }
        
        try {
            return standingsRenderer.render(tournament.getName(), standings, page);
        } catch (Exception e) {
            log.error("Failed to generate standings image for tournament {}", tournament.getId(), e);
            return createErrorImage("Failed to generate standings image");
        }
    }

    /**
     * Get fixtures image from cache or generate new one.
     * Cache key includes tournament ID, page, and the latest match update time (epoch millis).
     */
    @Cacheable(value = CacheConfig.IMAGE_CACHE, 
               key = "'fixtures:' + #tournament.id + ':' + #page + ':' + (#latestUpdate != null ? #latestUpdate.toEpochSecond(java.time.ZoneOffset.UTC) : 0)")
    public byte[] getFixturesImage(Tournament tournament, List<Match> matches, int page, LocalDateTime latestUpdate) {
        log.info("FIXTURES_IMAGE_GENERATE: tournamentId={}, page={}, latestUpdate={}", 
                tournament.getId(), page, latestUpdate);
        try {
            return fixturesRenderer.render(tournament.getName(), matches, page);
        } catch (Exception e) {
            log.error("Failed to generate fixtures image for tournament {}", tournament.getId(), e);
            return createErrorImage("Failed to generate fixtures image");
        }
    }

    /**
     * Get fixtures image for a specific round.
     */
    @Cacheable(value = CacheConfig.IMAGE_CACHE, 
               key = "'fixtures_round:' + #tournament.id + ':' + #roundNumber + ':' + #page + ':' + (#latestUpdate != null ? #latestUpdate.toEpochSecond(java.time.ZoneOffset.UTC) : 0)")
    public byte[] getFixturesRoundImage(Tournament tournament, int roundNumber, List<Match> roundMatches, int page, LocalDateTime latestUpdate) {
        log.info("FIXTURES_ROUND_IMAGE_GENERATE: tournamentId={}, round={}, page={}, latestUpdate={}", 
                tournament.getId(), roundNumber, page, latestUpdate);
        try {
            return fixturesRenderer.renderRound(tournament.getName(), roundNumber, roundMatches, page);
        } catch (Exception e) {
            log.error("Failed to generate fixtures round image for tournament {} round {}", tournament.getId(), roundNumber, e);
            return createErrorImage("Failed to generate fixtures image");
        }
    }

    /**
     * Generate caption for standings image.
     */
    public String getStandingsCaption(List<TeamStanding> standings) {
        return standingsRenderer.generateCaption(standings);
    }

    /**
     * Generate caption for fixtures image.
     */
    public String getFixturesCaption(List<Match> matches, Integer roundNumber) {
        return fixturesRenderer.generateCaption(matches, roundNumber);
    }

    /**
     * Get the latest update time from a list of matches.
     */
    public LocalDateTime getLatestMatchUpdate(List<Match> matches) {
        return matches.stream()
                .map(Match::getUpdatedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
    }

    public int getStandingsTotalPages(int totalItems) {
        return standingsRenderer.getTotalPages(totalItems);
    }

    public int getFixturesTotalPages(List<Match> matches) {
        return fixturesRenderer.getTotalPages(matches);
    }

    /**
     * Create a simple error image when rendering fails.
     */
    private byte[] createErrorImage(String message) {
        try {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(400, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = image.createGraphics();
            g2d.setColor(new java.awt.Color(0x1a, 0x1a, 0x2e));
            g2d.fillRect(0, 0, 400, 100);
            g2d.setColor(java.awt.Color.RED);
            g2d.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14));
            g2d.drawString(message, 50, 55);
            g2d.dispose();
            
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to create error image", e);
            return new byte[0];
        }
    }
}
