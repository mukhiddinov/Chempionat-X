package com.chempionat.bot.domain.repository;

import com.chempionat.bot.domain.model.OrganizerRequest;
import com.chempionat.bot.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizerRequestRepository extends JpaRepository<OrganizerRequest, Long> {
    List<OrganizerRequest> findByStatus(String status);
    Optional<OrganizerRequest> findByUserAndStatus(User user, String status);
}
