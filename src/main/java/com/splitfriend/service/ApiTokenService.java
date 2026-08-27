package com.splitfriend.service;

import com.splitfriend.model.User;
import com.splitfriend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Manages the per-user API token used by external clients (e.g. Home Assistant)
 * to read balances over the local network.
 *
 * The token is stored in plaintext so the profile page can redisplay it at any
 * time. This is an accepted trade-off for a LAN-only deployment.
 */
@Service
@Transactional
public class ApiTokenService {

    private static final Logger log = LoggerFactory.getLogger(ApiTokenService.class);

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiTokenService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @return a URL-safe, unpadded Base64 token carrying 256 bits of entropy
     */
    public String generateToken() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            byte[] bytes = new byte[TOKEN_BYTES];
            secureRandom.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            if (userRepository.findByApiToken(token).isEmpty()) {
                return token;
            }
            log.warn("Generated API token collided with an existing one, retrying");
        }
        throw new IllegalStateException("Unable to generate a unique API token after "
                + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    /**
     * Turns API access on, issuing a fresh token. Idempotent for an already
     * enabled user: the existing token is kept.
     */
    public User enable(User user) {
        if (Boolean.TRUE.equals(user.getApiTokenEnabled()) && user.getApiToken() != null) {
            return user;
        }
        return assignNewToken(user);
    }

    /**
     * Turns API access off and wipes the token, so a leaked value dies immediately.
     */
    public User disable(User user) {
        user.setApiTokenEnabled(false);
        user.setApiToken(null);
        user.setApiTokenCreatedAt(null);
        log.info("API token disabled for user {}", user.getEmail());
        return userRepository.save(user);
    }

    /**
     * Replaces the current token with a new one, leaving API access enabled.
     */
    public User regenerate(User user) {
        return assignNewToken(user);
    }

    /**
     * Resolves a token presented by a client to the owning user.
     *
     * @return the user, or empty if the token is unknown, if API access is
     *         disabled for that user, or if the account itself is disabled
     */
    @Transactional(readOnly = true)
    public Optional<User> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByApiToken(token)
                .filter(user -> Boolean.TRUE.equals(user.getApiTokenEnabled()))
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()));
    }

    private User assignNewToken(User user) {
        user.setApiToken(generateToken());
        user.setApiTokenEnabled(true);
        user.setApiTokenCreatedAt(LocalDateTime.now());
        log.info("API token issued for user {}", user.getEmail());
        return userRepository.save(user);
    }
}
