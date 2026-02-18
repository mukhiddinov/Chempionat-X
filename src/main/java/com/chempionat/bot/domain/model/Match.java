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

    @Column(name = "is_bye")
    private Boolean isBye;
    
    @Column(name = "reject_reason")
    private String rejectReason;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
