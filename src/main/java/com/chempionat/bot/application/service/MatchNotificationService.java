package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.MatchResult;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.infrastructure.telegram.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for sending match-related notifications to users
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchNotificationService {

    private final NotificationService notificationService;

    /**
     * Notify all participants when a round starts
     */
    public void notifyRoundStart(Tournament tournament, int roundNumber, List<Match> roundMatches) {
        log.info("Notifying participants of round {} start for tournament {}", 
                roundNumber, tournament.getId());
        
        for (Match match : roundMatches) {
            if (match.getIsBye()) {
                // Notify team about bye round
                notifyByeRound(match);
            } else {
                // Notify both participants about the match
                notifyMatchParticipants(match);
            }
        }
    }

    /**
     * Notify team about bye round (rest day)
     */
    private void notifyByeRound(Match match) {
        User user = match.getHomeTeam().getUser();
        
        String message = String.format(
                "🌙 Dam olish kuni\n\n" +
                "Turnir: %s\n" +
                "Tur: %d\n\n" +
                "Bu turda sizning o'yiningiz yo'q. Dam oling!",
                match.getTournament().getName(),
                match.getRound()
        );
        
        notificationService.notifyUser(user.getTelegramId(), message);
    }

    /**
     * Notify match participants about their upcoming match
     */
    public void notifyMatchParticipants(Match match) {
        if (match.getIsBye()) {
            notifyByeRound(match);
            return;
        }

        User homeUser = match.getHomeTeam().getUser();
        User awayUser = match.getAwayTeam().getUser();
        
        String homeUsername = homeUser.getUsername() != null ? 
                "@" + homeUser.getUsername() : homeUser.getFirstName();
        String awayUsername = awayUser.getUsername() != null ? 
                "@" + awayUser.getUsername() : awayUser.getFirstName();

        // Notify home team
        String homeMessage = String.format(
                "🏟 Yangi o'yin!\n\n" +
                "Turnir: %s\n" +
                "Tur: %d\n\n" +
                "Siz: %s (Uy egasi)\n" +
                "Raqib: %s\n\n" +
                "Siz uy egasi sifatida o'yin natijasini yuklashingiz kerak.\n" +
                "Buyruq: /submitresult %d",
                match.getTournament().getName(),
                match.getRound(),
                homeUsername,
                awayUsername,
                match.getId()
        );

        notificationService.notifyUser(homeUser.getTelegramId(), homeMessage);

        // Notify away team
        String awayMessage = String.format(
                "⚽️ Yangi o'yin!\n\n" +
                "Turnir: %s\n" +
                "Tur: %d\n\n" +
                "Raqib: %s (Uy egasi)\n" +
                "Siz: %s\n\n" +
                "Uy egasi natijani yuborishi kerak.",
                match.getTournament().getName(),
                match.getRound(),
                homeUsername,
                awayUsername
        );
        
        notificationService.notifyUser(awayUser.getTelegramId(), awayMessage);
    }

    /**
     * Notify organizer about new result submission
     */
    public void notifyOrganizerOfNewResult(MatchResult result) {
        Match match = result.getMatch();
        Tournament tournament = match.getTournament();
        User organizer = tournament.getCreatedBy();
        
        String homeUsername = match.getHomeTeam().getUser().getUsername() != null ? 
                "@" + match.getHomeTeam().getUser().getUsername() : 
                match.getHomeTeam().getUser().getFirstName();
        String awayUsername = match.getAwayTeam().getUser().getUsername() != null ? 
                "@" + match.getAwayTeam().getUser().getUsername() : 
                match.getAwayTeam().getUser().getFirstName();

        String message = String.format(
                "🏟 Yangi natija!\n\n" +
                "Turnir: %s\n" +
                "O'yin: %s vs %s\n" +
                "Natija: %d:%d\n\n" +
                "Tasdiqlaysizmi?",
                tournament.getName(),
                homeUsername,
                awayUsername,
                result.getHomeScore(),
                result.getAwayScore()
        );
        
        // This will be handled by MatchResultService which already has this logic
        log.info("Notifying organizer {} about result submission for match {}", 
                organizer.getTelegramId(), match.getId());
    }
}
