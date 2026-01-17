package com.splitfriend.security;

import com.splitfriend.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class SecurityUtils {

    private SecurityUtils() {
        // Utility class
    }

    public static Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetailsService.CustomUserDetails) {
            return Optional.of(((CustomUserDetailsService.CustomUserDetails) principal).getUser());
        }

        return Optional.empty();
    }

    public static Optional<Long> getCurrentUserId() {
        return getCurrentUser().map(User::getId);
    }

    public static Optional<String> getCurrentUserEmail() {
        return getCurrentUser().map(User::getEmail);
    }

    public static boolean isCurrentUserAdmin() {
        return getCurrentUser()
                .map(user -> user.getRole().name().equals("ADMIN"))
                .orElse(false);
    }
}
