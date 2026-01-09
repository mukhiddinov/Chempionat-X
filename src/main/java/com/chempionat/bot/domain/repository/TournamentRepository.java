package com.chempionat.bot.domain.repository;

import com.chempionat.bot.domain.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    List<Tournament> findByIsActiveTrue();
}
