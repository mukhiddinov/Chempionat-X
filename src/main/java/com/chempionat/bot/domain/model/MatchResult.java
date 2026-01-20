package com.chempionat.bot.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a submitted match result waiting for admin approval.
 */
@Entity
@Table(name = "match_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "home_score", nullable = false)
    private Integer homeScore;

    @Column(name = "away_score", nullable = false)
    private Integer awayScore;

    @Column(name = "screenshot_url")
    private String screenshotUrl;

    @ManyToOne
    @JoinColumn(name = "submitted_by_user_id", nullable = false)
    private User submittedBy;

    @Column(name = "is_approved")
    private Boolean isApproved;

    @ManyToOne
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "review_comment")
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
        if (isApproved == null) {
            isApproved = false;
        }
    }
}
