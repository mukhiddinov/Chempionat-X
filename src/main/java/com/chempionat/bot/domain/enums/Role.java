package com.chempionat.bot.domain.enums;

public enum Role {
    USER,
    ORGANIZER,  // Can create and manage their own tournaments
    MODERATOR,  // Can moderate tournaments (legacy, similar to ORGANIZER)
    ADMIN       // Full system access, can see and manage ALL tournaments
}
