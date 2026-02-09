package com.chempionat.bot.infrastructure.rest;

import com.chempionat.bot.application.factory.TournamentFactory;
import com.chempionat.bot.application.service.UserService;
import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentFactory tournamentFactory;
    private final TournamentRepository tournamentRepository;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<TournamentResponse> createTournament(
            @Valid @RequestBody CreateTournamentRequest request) {
        
        log.info("Creating tournament: {} by telegramId: {}", request.name, request.creatorTelegramId);
        
        User creator = userService.getUserByTelegramId(request.creatorTelegramId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        Tournament tournament = tournamentFactory.createTournament(
                request.type,
                request.name,
                request.description,
                creator
        );
        
        Tournament saved = tournamentRepository.save(tournament);
        log.info("Tournament created: id={}, name={}", saved.getId(), saved.getName());
        
        return ResponseEntity.ok(TournamentResponse.from(saved));
    }

    @GetMapping
    public ResponseEntity<List<TournamentResponse>> getAllTournaments() {
        List<Tournament> tournaments = tournamentRepository.findAll();
        return ResponseEntity.ok(
                tournaments.stream()
                        .map(TournamentResponse::from)
                        .toList()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<TournamentResponse> getTournament(@PathVariable Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));
        return ResponseEntity.ok(TournamentResponse.from(tournament));
    }

    @GetMapping("/active")
    public ResponseEntity<List<TournamentResponse>> getActiveTournaments() {
        List<Tournament> tournaments = tournamentRepository.findByIsActiveTrue();
        return ResponseEntity.ok(
                tournaments.stream()
                        .map(TournamentResponse::from)
                        .toList()
        );
    }

    // DTOs
    public record CreateTournamentRequest(
            @NotBlank(message = "Tournament name is required")
            String name,
            
            String description,
            
            @NotNull(message = "Tournament type is required")
            TournamentType type,
            
            @NotNull(message = "Creator telegram ID is required")
            Long creatorTelegramId
    ) {}

    public record TournamentResponse(
            Long id,
            String name,
            String description,
            TournamentType type,
            boolean isActive,
            String creatorUsername,
            String createdAt
    ) {
        public static TournamentResponse from(Tournament tournament) {
            return new TournamentResponse(
                    tournament.getId(),
                    tournament.getName(),
                    tournament.getDescription(),
                    tournament.getType(),
                    tournament.getIsActive(),
                    tournament.getCreatedBy().getUsername(),
                    tournament.getCreatedAt().toString()
            );
        }
    }
}
