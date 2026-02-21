package com.chempionat.bot.domain.model;

import com.chempionat.bot.domain.enums.MatchLifecycleState;
import com.chempionat.bot.domain.enums.MatchStage;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "matches", indexes = {
    @Index(name = "idx_tournament_id", columnList = "tournament_id"),
    @Index(name = "idx_state", columnList = "state")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic locking version field.
     * Prevents concurrent modifications to the same match.
     * JPA will throw OptimisticLockException if version mismatch.
     */
    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private MatchLifecycleState state;

    @Column(name = "round")
    private Integer round;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage")
    private MatchStage stage;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    /**
     * Home team penalty score (only used when match ends in draw during knockout).
     * Null means no penalty shootout occurred.
     */
    @Column(name = "home_penalty_score")
    private Integer homePenaltyScore;

    /**
     * Away team penalty score (only used when match ends in draw during knockout).
     * Null means no penalty shootout occurred.
     */
    @Column(name = "away_penalty_score")
    private Integer awayPenaltyScore;

    /**
     * Indicates if this match required penalty shootout to determine winner.
     */
    @Column(name = "decided_by_penalties")
    private Boolean decidedByPenalties;

    /**
     * True if this is a third-place playoff match (loser semifinal match).
     */
    @Column(name = "is_third_place_match")
    private Boolean isThirdPlaceMatch;

    @Column(name = "is_bye")
    private Boolean isBye;
    
    @Column(name = "reject_reason")
    private String rejectReason;
    
    /**
     * Position in bracket tree for knockout tournaments (1-based).
     * Used for ordering and rendering bracket visualization.
     */
    @Column(name = "bracket_position")
    private Integer bracketPosition;
    
    /**
     * Reference to the next match where winner advances.
     * Null for final match.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_match_id")
    private Match nextMatch;
    
    /**
     * True if winner goes to home_team slot of next match,
     * False if winner goes to away_team slot.
     */
    @Column(name = "winner_to_home")
    private Boolean winnerToHome;

    @OneToOne(mappedBy = "match", cascade = CascadeType.ALL)
    private MatchResult result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (state == null) {
            state = MatchLifecycleState.CREATED;
        }
        if (isBye == null) {
            isBye = false;
        }
        if (decidedByPenalties == null) {
            decidedByPenalties = false;
        }
        if (isThirdPlaceMatch == null) {
            isThirdPlaceMatch = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
