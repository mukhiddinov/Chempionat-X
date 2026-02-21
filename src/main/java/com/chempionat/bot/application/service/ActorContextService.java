package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized service for actor identity resolution.
 * 
 * This service provides a clean separation between:
 * - Telegram transport layer (chat_id - where messages are sent)
 * - Business actor identity (actor_id - who is performing the action)
 * 
 * When an admin impersonates an organizer:
 * - Messages still go to admin's chat (chat_id remains admin's)
 * - But all business logic uses organizer's ID (effective actor)
 * 
 * Thread-safe: Multiple admins can impersonate different organizers simultaneously.
 */
@Slf4j
@Service
public class ActorContextService {

    private final UserRepository userRepository;
    private final UserService userService;
    
    // Map: admin's telegramId -> impersonated organizer's database ID
    private final Map<Long, Long> activeImpersonations = new ConcurrentHashMap<>();

    public ActorContextService(UserRepository userRepository, @Lazy UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /**
     * Get the effective actor's Telegram ID for business logic.
     * 
     * If the user is an admin with active impersonation, returns the impersonated
     * organizer's Telegram ID. Otherwise returns the original user's Telegram ID.
     * 
     * @param telegramUserId The Telegram user ID from the update
     * @return The effective Telegram ID to use for business operations
     */
    public Long getEffectiveActorTelegramId(Long telegramUserId) {
        if (telegramUserId == null) {
            return null;
        }
        
        // Check if this user has an active impersonation
        Long impersonatedOrganizerId = activeImpersonations.get(telegramUserId);
        
        if (impersonatedOrganizerId != null) {
            // Verify the impersonated user still exists and get their telegram ID
            Optional<User> organizerOpt = userRepository.findById(impersonatedOrganizerId);
            if (organizerOpt.isPresent()) {
                log.debug("Actor resolution: telegram {} -> impersonating organizer {} (db id: {})", 
                         telegramUserId, organizerOpt.get().getTelegramId(), impersonatedOrganizerId);
                return organizerOpt.get().getTelegramId();
            } else {
                // Organizer no longer exists, clear impersonation
                log.warn("Impersonated organizer {} no longer exists, clearing impersonation for admin {}", 
                        impersonatedOrganizerId, telegramUserId);
                activeImpersonations.remove(telegramUserId);
            }
        }
        
        return telegramUserId;
    }

    /**
     * Get the effective actor User object for business logic.
     * 
     * If the user is an admin with active impersonation, returns the impersonated
     * organizer's User object. Otherwise returns the original user.
     * 
     * @param telegramUserId The Telegram user ID from the update
     * @return The effective User to use for business operations, or empty if not found
     */
    public Optional<User> getEffectiveActor(Long telegramUserId) {
        if (telegramUserId == null) {
            return Optional.empty();
        }
        
        // Check if this user has an active impersonation
        Long impersonatedOrganizerId = activeImpersonations.get(telegramUserId);
        
        if (impersonatedOrganizerId != null) {
            // Return the impersonated organizer
            Optional<User> organizerOpt = userRepository.findById(impersonatedOrganizerId);
            if (organizerOpt.isPresent()) {
                log.debug("Returning impersonated user {} for telegram {}", 
                         impersonatedOrganizerId, telegramUserId);
                return organizerOpt;
            } else {
                // Organizer no longer exists, clear impersonation
                log.warn("Impersonated organizer {} no longer exists, clearing impersonation", 
                        impersonatedOrganizerId);
                activeImpersonations.remove(telegramUserId);
            }
        }
        
        // Return the actual user
        return userRepository.findByTelegramId(telegramUserId);
    }

    /**
     * Get the real user (admin), not the impersonated one.
     * Use this when you need the actual person interacting with the bot.
     * 
     * @param telegramUserId The Telegram user ID from the update
     * @return The real User object, or empty if not found
     */
    public Optional<User> getRealUser(Long telegramUserId) {
        return userRepository.findByTelegramId(telegramUserId);
    }

    /**
     * Start impersonation of an organizer by an admin.
     * 
     * @param adminTelegramId The admin's Telegram ID
     * @param organizerDatabaseId The organizer's database ID to impersonate
     * @return true if impersonation started successfully, false otherwise
     */
    public boolean startImpersonation(Long adminTelegramId, Long organizerDatabaseId) {
        if (adminTelegramId == null || organizerDatabaseId == null) {
            return false;
        }

        // Verify admin exists and has ADMIN role
        Optional<User> adminOpt = userRepository.findByTelegramId(adminTelegramId);
        if (adminOpt.isEmpty() || adminOpt.get().getRole() != Role.ADMIN) {
            log.warn("Attempted impersonation by non-admin user: {}", adminTelegramId);
            return false;
        }

        // Verify organizer exists
        Optional<User> organizerOpt = userRepository.findById(organizerDatabaseId);
        if (organizerOpt.isEmpty()) {
            log.warn("Attempted to impersonate non-existent user: {}", organizerDatabaseId);
            return false;
        }

        User organizer = organizerOpt.get();
        
        // Store impersonation mapping
        activeImpersonations.put(adminTelegramId, organizerDatabaseId);
        
        log.info("Admin {} started impersonating organizer {} (telegram: {})", 
                adminTelegramId, organizerDatabaseId, organizer.getTelegramId());
        
        return true;
    }

    /**
     * Exit impersonation mode for an admin.
     * 
     * @param adminTelegramId The admin's Telegram ID
     */
    public void exitImpersonation(Long adminTelegramId) {
        Long removed = activeImpersonations.remove(adminTelegramId);
        if (removed != null) {
            log.info("Admin {} exited impersonation of organizer {}", adminTelegramId, removed);
        }
    }

    /**
     * Check if a user is currently impersonating someone.
     * 
     * @param telegramUserId The Telegram user ID to check
     * @return true if the user has an active impersonation
     */
    public boolean isImpersonating(Long telegramUserId) {
        return activeImpersonations.containsKey(telegramUserId);
    }

    /**
     * Get the database ID of the impersonated organizer.
     * 
     * @param adminTelegramId The admin's Telegram ID
     * @return The impersonated organizer's database ID, or null if not impersonating
     */
    public Long getImpersonatedOrganizerId(Long adminTelegramId) {
        return activeImpersonations.get(adminTelegramId);
    }

    /**
     * Get the impersonated organizer User object.
     * 
     * @param adminTelegramId The admin's Telegram ID
     * @return The impersonated organizer, or empty if not impersonating
     */
    public Optional<User> getImpersonatedOrganizer(Long adminTelegramId) {
        Long organizerId = activeImpersonations.get(adminTelegramId);
        if (organizerId == null) {
            return Optional.empty();
        }
        return userRepository.findById(organizerId);
    }

    /**
     * Clear all active impersonations. For testing/admin purposes.
     */
    public void clearAllImpersonations() {
        log.warn("Clearing all {} active impersonations", activeImpersonations.size());
        activeImpersonations.clear();
    }
}
