package com.chempionat.bot.application.factory;

import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Factory for creating tournaments with different configurations.
 * Implements Factory pattern for tournament creation.
 */
@Slf4j
@Component
public class TournamentFactory {
    
    public Tournament createLeagueTournament(String name, String description, User creator) {
        log.info("Creating league tournament: {}", name);
        
        return TournamentBuilder.builder()
                .name(name)
                .description(description)
                .type(TournamentType.LEAGUE)
                .createdBy(creator)
                .build()
                .build();
    }
    
    public Tournament createPlayoffTournament(String name, String description, User creator) {
        log.info("Creating playoff tournament: {}", name);
        
        return TournamentBuilder.builder()
                .name(name)
                .description(description)
                .type(TournamentType.PLAYOFF)
                .createdBy(creator)
                .build()
                .build();
    }
    
    public Tournament createTournament(TournamentType type, String name, String description, User creator) {
        log.info("Creating {} tournament: {}", type, name);
        
        return TournamentBuilder.builder()
                .name(name)
                .description(description)
                .type(type)
                .createdBy(creator)
                .build()
                .build();
    }
}
