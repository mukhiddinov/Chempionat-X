package com.chempionat.bot.application.service;

import com.chempionat.bot.domain.enums.Role;
import com.chempionat.bot.domain.model.User;
import com.chempionat.bot.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User getOrCreateUser(Long telegramId, String username, String firstName, String lastName) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> createUser(telegramId, username, firstName, lastName));
    }

    private User createUser(Long telegramId, String username, String firstName, String lastName) {
        User user = User.builder()
                .telegramId(telegramId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .role(Role.USER)
                .build();
        
        User savedUser = userRepository.save(user);
        log.info("Created new user: telegramId={}, id={}", telegramId, savedUser.getId());
        return savedUser;
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }
}
