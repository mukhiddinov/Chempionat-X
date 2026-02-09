package com.chempionat.bot.application.service;

import com.chempionat.bot.application.factory.TournamentFactory;
import com.chempionat.bot.application.strategy.TournamentScheduleStrategy;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.Team;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.MatchRepository;
import com.chempionat.bot.domain.repository.TeamRepository;
import com.chempionat.bot.domain.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final TournamentFactory tournamentFactory;
    private final Map<String, TournamentScheduleStrategy> scheduleStrategies;

    @Transactional
    public Tournament createTournament(String name, String description, TournamentType type, User creator) {
        log.info("Creating tournament: name={}, type={}, creator={}", name, type, creator.getTelegramId());
        
        Tournament tournament = tournamentFactory.createTournament(type, name, description, creator);
        tournament = tournamentRepository.save(tournament);
        
        log.info("Tournament created: id={}, name={}", tournament.getId(), tournament.getName());
        return tournament;
    }

    @Transactional(readOnly = true)
    public List<Tournament> getAllTournaments() {
        return tournamentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Tournament> getActiveTournaments() {
        return tournamentRepository.findByIsActiveTrue();
    }

    @Transactional(readOnly = true)
    public Optional<Tournament> getTournamentById(Long id) {
        return tournamentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Tournament> getUserTournaments(User user) {
        return tournamentRepository.findByCreatedBy(user);
    }

    @Transactional(readOnly = true)
    public List<Tournament> getTournamentsForPlayer(User user) {
        return tournamentRepository.findTournamentsByPlayerId(user.getId());
    }

    @Transactional
    public Team joinTournament(Tournament tournament, User user, String teamName) {
        log.info("User {} joining tournament {}", user.getTelegramId(), tournament.getId());
        
        // Check if user already joined
        Optional<Team> existingTeam = teamRepository.findByTournamentAndUser(tournament, user);
        if (existingTeam.isPresent()) {
            log.warn("User {} already joined tournament {}", user.getTelegramId(), tournament.getId());
            throw new IllegalStateException("You have already joined this tournament");
        }

        Team team = Team.builder()
                .name(teamName)
                .tournament(tournament)
                .user(user)
                .build();

        team = teamRepository.save(team);
        log.info("Team created: id={}, name={}, tournament={}", team.getId(), teamName, tournament.getId());
        
        return team;
    }

    @Transactional
    public void startTournament(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Tournament not found: " + tournamentId));

        if (tournament.getStartDate() != null && tournament.getStartDate().isBefore(LocalDateTime.now())) {
            log.warn("Tournament {} already started", tournamentId);
            return;
        }

        List<Team> teams = teamRepository.findByTournament(tournament);
        if (teams.isEmpty()) {
            throw new IllegalStateException("Cannot start tournament without teams");
        }

        // Get the appropriate scheduling strategy
        TournamentScheduleStrategy strategy = getStrategy(tournament.getType());
        
        // Generate matches
        List<Match> matches = strategy.generateMatches(tournament, teams);
        matchRepository.saveAll(matches);

        tournament.setStartDate(LocalDateTime.now());
        tournament.setIsActive(true);
        tournamentRepository.save(tournament);

        log.info("Tournament {} started with {} matches", tournamentId, matches.size());
    }

    @Transactional(readOnly = true)
    public List<Team> getTournamentTeams(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Tournament not found: " + tournamentId));
        return teamRepository.findByTournament(tournament);
    }

    @Transactional(readOnly = true)
    public List<Match> getTournamentMatches(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Tournament not found: " + tournamentId));
        return matchRepository.findByTournament(tournament);
    }

    @Transactional
    public void updateTournament(Long tournamentId, String name, String description) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Tournament not found: " + tournamentId));
        
        if (name != null) {
            tournament.setName(name);
        }
        if (description != null) {
            tournament.setDescription(description);
        }
        
        tournamentRepository.save(tournament);
        log.info("Tournament {} updated", tournamentId);
    }
    
    @Transactional
    public Tournament updateTournament(Tournament tournament) {
        return tournamentRepository.save(tournament);
    }

    @Transactional
    public void deleteTournament(Long tournamentId) {
        tournamentRepository.deleteById(tournamentId);
        log.info("Tournament {} deleted", tournamentId);
    }

    @Transactional
    public void endTournament(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Tournament not found: " + tournamentId));
        
        tournament.setIsActive(false);
        tournament.setEndDate(LocalDateTime.now());
        tournamentRepository.save(tournament);
        
        log.info("Tournament {} ended", tournamentId);
    }

    @Transactional(readOnly = true)
    public boolean isOwner(Long tournamentId, User user) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Tournament not found: " + tournamentId));
        return tournament.getCreatedBy().getId().equals(user.getId());
    }

    private TournamentScheduleStrategy getStrategy(TournamentType type) {
        String strategyName = type == TournamentType.LEAGUE ? "leagueScheduleStrategy" : "playoffScheduleStrategy";
        TournamentScheduleStrategy strategy = scheduleStrategies.get(strategyName);
        
        if (strategy == null) {
            throw new IllegalStateException("No strategy found for tournament type: " + type);
        }
        
        return strategy;
    }
}
