package com.chempionat.bot.application.factory;

import com.chempionat.bot.domain.enums.TournamentType;
import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Builder for creating Tournament entities with validation.
 * Implements the Builder pattern for flexible tournament creation.
 */
@Getter
@Builder
public class TournamentBuilder {
    
    private String name;
    private String description;
    private TournamentType type;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private User createdBy;
    
    public Tournament build() {
        validateTournament();
        
        return Tournament.builder()
                .name(name)
                .description(description)
                .type(type)
                .startDate(startDate)
                .endDate(endDate)
                .createdBy(createdBy)
                .isActive(false)
                .build();
    }
    
    private void validateTournament() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tournament name is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Tournament type is required");
        }
        if (createdBy == null) {
            throw new IllegalArgumentException("Tournament creator is required");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
    }
}
