package com.chempionat.bot.domain.repository;

import com.chempionat.bot.domain.model.Match;
import com.chempionat.bot.domain.model.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
    
    Optional<MatchResult> findByMatch(Match match);
    
    List<MatchResult> findByIsApprovedFalseAndReviewedByIsNull();
    
    List<MatchResult> findByIsApprovedTrue();
}
