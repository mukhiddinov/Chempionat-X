package com.chempionat.bot.domain.enums;

public enum MatchLifecycleState {
    CREATED,
    PLAYED,
    PENDING_APPROVAL,
    /**
     * Match ended in a draw during knockout - awaiting penalty shootout result.
     * Only applicable for PLAYOFF tournament type.
     */
    PENDING_PENALTY,
    APPROVED,
    REJECTED
}
