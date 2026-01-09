package com.chempionat.bot.domain.repository;

import com.chempionat.bot.domain.model.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
}
