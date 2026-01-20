package com.chempionat.bot.domain.repository;

import com.chempionat.bot.domain.model.Tournament;
import com.chempionat.bot.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    List<Tournament> findByIsActiveTrue();
    
    List<Tournament> findByCreatedBy(User user);
    
    @Query("SELECT DISTINCT t FROM Tournament t JOIN Team team ON team.tournament = t WHERE team.user.id = :userId")
    List<Tournament> findTournamentsByPlayerId(@Param("userId") Long userId);
}
