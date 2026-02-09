package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    public List<Match> getTodaysMatchesForUser(User user) {
        List<Team> userTeams = teamRepository.findByUser(user);
        
        List<Match> todaysMatches = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (Team team : userTeams) {
            List<Match> matches = matchRepository.findTodaysMatchesForTeam(team, today);
            todaysMatches.addAll(matches);
        }
        
        log.debug("Found {} matches for user {}", todaysMatches.size(), user.getId());
        return todaysMatches;
    }
    
    public boolean isHomePlayer(Match match, User user) {
        return match.getHomeTeam().getUser().getId().equals(user.getId());
    }
    
    public boolean isHomePlayer(Match match, Long telegramId) {
        return match.getHomeTeam().getUser().getTelegramId().equals(telegramId);
    }
}
