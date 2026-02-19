package com.chempionat.bot.domain.enums;

/**
 * Represents the lifecycle status of a tournament.
 * Supports proper state transitions for tournament management.
 */
public enum TournamentStatus {
    /**
     * Tournament has been created but registration hasn't opened yet.
     */
    CREATED,
    
    /**
     * Tournament is open for team registration.
     */
    REGISTRATION,
    
    /**
     * Tournament has started (matches generated).
     */
    STARTED,
    
    /**
     * Tournament is actively being played (some matches completed).
     */
    IN_PROGRESS,
    
    /**
     * Tournament has finished (all matches completed, winner determined).
     */
    FINISHED,
    
    /**
     * Tournament was cancelled before completion.
     */
    CANCELLED;
    
    /**
     * Check if tournament can accept new registrations.
     */
    public boolean canRegister() {
        return this == CREATED || this == REGISTRATION;
    }
    
    /**
     * Check if tournament can be started.
     */
    public boolean canStart() {
        return this == CREATED || this == REGISTRATION;
    }
    
    /**
     * Check if tournament is actively running (matches can be played).
     */
    public boolean isActive() {
        return this == STARTED || this == IN_PROGRESS;
    }
    
    /**
     * Check if tournament has ended (finished or cancelled).
     */
    public boolean isEnded() {
        return this == FINISHED || this == CANCELLED;
    }
}
