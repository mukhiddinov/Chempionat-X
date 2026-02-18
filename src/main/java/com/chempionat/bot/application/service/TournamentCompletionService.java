package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.TeamRepository;
import com.chempionat.bot.domain.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service to check tournament completion and send end-of-tournament notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentCompletionService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final TournamentRepository tournamentRepository;
    private final StandingsImageRenderer standingsImageRenderer;
    private final NotificationService notificationService;

    /**
     * Check if all real matches (excluding byes) in a tournament are completed.
     * If so, send standings notification to all participants.
     * Note: This runs async after the calling transaction commits.
     */
    @Async
    public void checkAndNotifyIfComplete(Tournament tournament) {
        try {
            // Small delay to ensure the calling transaction has committed
            Thread.sleep(500);
            
            // Re-fetch tournament from DB to get fresh data
            Tournament freshTournament = tournamentRepository.findById(tournament.getId())
                    .orElse(null);
            if (freshTournament == null) {
                log.warn("Tournament {} not found when checking completion", tournament.getId());
                return;
            }
            
            doCheckAndNotify(freshTournament);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while checking tournament completion");
        } catch (Exception e) {
            log.error("Error checking tournament completion for tournament {}", tournament.getId(), e);
        }
    }
    
    @Transactional(readOnly = true)
    protected void doCheckAndNotify(Tournament tournament) {
        List<Match> allMatches = matchRepository.findByTournament(tournament);
        
        // Filter to real matches only (exclude byes and self-matches)
        List<Match> realMatches = allMatches.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsBye()))
                .filter(m -> m.getHomeTeam() != null && m.getAwayTeam() != null)
                .filter(m -> !m.getHomeTeam().getId().equals(m.getAwayTeam().getId()))
                .toList();
        
        if (realMatches.isEmpty()) {
            log.debug("No real matches in tournament {}", tournament.getId());
            return;
        }
        
        // Check if all real matches have scores
        long completedCount = realMatches.stream()
                .filter(m -> m.getHomeScore() != null && m.getAwayScore() != null)
                .count();
        
        if (completedCount < realMatches.size()) {
            log.debug("Tournament {} not complete: {}/{} matches", 
                    tournament.getId(), completedCount, realMatches.size());
            return;
        }
        
        // All matches completed - send notifications
        log.info("Tournament {} completed! All {} matches finished. Sending notifications...", 
                tournament.getId(), realMatches.size());
        
        sendCompletionNotifications(tournament, realMatches);
    }

    private void sendCompletionNotifications(Tournament tournament, List<Match> matches) {
        try {
            // Get all teams/participants
            List<Team> teams = teamRepository.findByTournament(tournament);
            
            if (teams.isEmpty()) {
                log.warn("No teams found for tournament {}", tournament.getId());
                return;
            }
            
            // Calculate final standings
            List<TeamStanding> standings = calculateStandings(teams, matches);
            
            // Generate standings image
            byte[] imageData = standingsImageRenderer.render(tournament.getName(), standings, 0);
            
            // Build congratulations message
            String congratsMessage = buildCongratsMessage(tournament, standings);
            
            // Send to all participants
            for (Team team : teams) {
                if (team.getUser() != null && team.getUser().getTelegramId() != null) {
                    try {
                        String personalMessage = buildPersonalMessage(team, standings, congratsMessage);
                        notificationService.notifyUserWithImage(
                                team.getUser().getTelegramId(),
                                imageData,
                                "standings_final_" + tournament.getId() + ".png",
                                personalMessage
                        );
                        log.debug("Sent completion notification to user {}", team.getUser().getTelegramId());
                    } catch (Exception e) {
                        log.error("Failed to send notification to user {}", team.getUser().getTelegramId(), e);
                    }
                }
            }
            
            log.info("Completion notifications sent to {} participants for tournament {}", 
                    teams.size(), tournament.getId());
            
        } catch (Exception e) {
            log.error("Error sending completion notifications for tournament {}", tournament.getId(), e);
        }
    }

    private List<TeamStanding> calculateStandings(List<Team> teams, List<Match> matches) {
        Map<Long, TeamStanding> standingsMap = new HashMap<>();
        
        // Initialize standings for all teams
        for (Team team : teams) {
            standingsMap.put(team.getId(), new TeamStanding(team.getId(), team.getName()));
        }
        
        // Calculate stats from matches (only real matches with scores)
        for (Match match : matches) {
            if (match.getHomeScore() == null || match.getAwayScore() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(match.getIsBye())) {
                continue;
            }
            
            TeamStanding homeStanding = standingsMap.get(match.getHomeTeam().getId());
            TeamStanding awayStanding = standingsMap.get(match.getAwayTeam().getId());
            
            if (homeStanding == null || awayStanding == null) {
                continue;
            }
            
            homeStanding.addMatch(match.getHomeScore(), match.getAwayScore());
            awayStanding.addMatch(match.getAwayScore(), match.getHomeScore());
        }
        
        // Sort standings
        return standingsMap.values().stream()
                .sorted()
                .collect(Collectors.toList());
    }

    private String buildCongratsMessage(Tournament tournament, List<TeamStanding> standings) {
        StringBuilder sb = new StringBuilder();
        sb.append("🏆 ").append(tournament.getName()).append(" tugadi!\n\n");
        sb.append("🎉 Yakuniy natijalar:\n\n");
        
        if (standings.size() >= 1) {
            sb.append("🥇 1-o'rin: ").append(standings.get(0).getTeamName()).append("\n");
        }
        if (standings.size() >= 2) {
            sb.append("🥈 2-o'rin: ").append(standings.get(1).getTeamName()).append("\n");
        }
        if (standings.size() >= 3) {
            sb.append("🥉 3-o'rin: ").append(standings.get(2).getTeamName()).append("\n");
        }
        
        return sb.toString();
    }

    private String buildPersonalMessage(Team team, List<TeamStanding> standings, String baseMessage) {
        // Find team's position
        int position = -1;
        for (int i = 0; i < standings.size(); i++) {
            if (standings.get(i).getTeamId().equals(team.getId())) {
                position = i + 1;
                break;
            }
        }
        
        StringBuilder sb = new StringBuilder(baseMessage);
        sb.append("\n");
        
        if (position == 1) {
            sb.append("🎊 Tabriklaymiz! Siz g'olib bo'ldingiz! 🏆");
        } else if (position == 2) {
            sb.append("🎊 Tabriklaymiz! Siz 2-o'rinni egallading! 🥈");
        } else if (position == 3) {
            sb.append("🎊 Tabriklaymiz! Siz 3-o'rinni egallading! 🥉");
        } else if (position > 0) {
            sb.append("📊 Sizning o'rningiz: ").append(position);
        }
        
        return sb.toString();
    }
}
