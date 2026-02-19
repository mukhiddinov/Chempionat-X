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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final TournamentFactory tournamentFactory;
    private final Map<String, TournamentScheduleStrategy> scheduleStrategies;
    private final NotificationService notificationService;

    public TournamentService(
            TournamentRepository tournamentRepository,
            TeamRepository teamRepository,
            MatchRepository matchRepository,
            TournamentFactory tournamentFactory,
            Map<String, TournamentScheduleStrategy> scheduleStrategies,
            @Lazy NotificationService notificationService) {
        this.tournamentRepository = tournamentRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.tournamentFactory = tournamentFactory;
        this.scheduleStrategies = scheduleStrategies;
        this.notificationService = notificationService;
    }

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
        
        // Check if tournament already started
        if (tournament.getStartDate() != null) {
            throw new IllegalStateException("Tournament has already started");
        }
        
        // Check if user already joined
        Optional<Team> existingTeam = teamRepository.findByTournamentAndUser(tournament, user);
        if (existingTeam.isPresent()) {
            log.warn("User {} already joined tournament {}", user.getTelegramId(), tournament.getId());
            throw new IllegalStateException("You have already joined this tournament");
        }
        
        // Check if team name is unique in this tournament (case-insensitive)
        if (teamRepository.existsByTournamentAndNameIgnoreCase(tournament, teamName)) {
            log.warn("Team name '{}' already exists in tournament {}", teamName, tournament.getId());
            throw new IllegalStateException("Bu nomdagi jamoa allaqachon mavjud. Iltimos boshqa nom tanlang.");
        }
        
        // Check if tournament is full
        long currentParticipants = teamRepository.countByTournament(tournament);
        Integer maxParticipants = tournament.getMaxParticipants();
        if (maxParticipants != null && currentParticipants >= maxParticipants) {
            throw new IllegalStateException("Tournament is full");
        }

        Team team = Team.builder()
                .name(teamName)
                .tournament(tournament)
                .user(user)
                .build();

        team = teamRepository.save(team);
        log.info("Team created: id={}, name={}, tournament={}", team.getId(), teamName, tournament.getId());
        
        // Check auto-start condition: if autoStart is enabled and max participants reached
        if (Boolean.TRUE.equals(tournament.getAutoStart()) && maxParticipants != null) {
            long newParticipantCount = currentParticipants + 1;
            if (newParticipantCount >= maxParticipants) {
                log.info("Auto-starting tournament {} - max participants ({}) reached", 
                        tournament.getId(), maxParticipants);
                startTournament(tournament.getId());
            }
        }
        
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
        
        // Notify all participants that tournament has started
        notifyParticipantsOnStart(tournament, teams, matches);
    }
    
    /**
     * Send notification to all tournament participants that the tournament has started.
     */
    private void notifyParticipantsOnStart(Tournament tournament, List<Team> teams, List<Match> matches) {
        // Count real matches (exclude byes)
        long realMatchCount = matches.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsBye()))
                .filter(m -> m.getHomeTeam() != null && m.getAwayTeam() != null)
                .filter(m -> !m.getHomeTeam().getId().equals(m.getAwayTeam().getId()))
                .count();
        
        String baseMessage = String.format(
                "🏆 Turnir boshlandi!\n\n" +
                "📋 %s\n" +
                "👥 Ishtirokchilar: %d\n" +
                "⚽ Jami o'yinlar: %d\n\n" +
                "O'yinlaringizni ko'rish uchun /mytournaments buyrug'ini yuboring.\n" +
                "Omad tilaymiz! 🍀",
                tournament.getName(),
                teams.size(),
                realMatchCount
        );
        
        for (Team team : teams) {
            if (team.getUser() != null && team.getUser().getTelegramId() != null) {
                try {
                    notificationService.notifyUser(team.getUser().getTelegramId(), baseMessage);
                    log.debug("Sent start notification to user {}", team.getUser().getTelegramId());
                } catch (Exception e) {
                    log.error("Failed to send start notification to user {}", 
                            team.getUser().getTelegramId(), e);
                }
            }
        }
        
        log.info("Sent tournament start notifications to {} participants", teams.size());
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
