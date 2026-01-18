package com.splitfriend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationEventListener.class);

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        log.info("Authentication SUCCESS for user: {}", username);
        log.debug("Authentication details: authorities={}, principal={}",
                event.getAuthentication().getAuthorities(),
                event.getAuthentication().getPrincipal().getClass().getSimpleName());
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String failureReason = event.getException().getClass().getSimpleName();
        String failureMessage = event.getException().getMessage();

        log.warn("Authentication FAILED for user: {} - Reason: {} - Message: {}",
                username, failureReason, failureMessage);

        // Log more specific failure reasons
        if (event.getException() instanceof org.springframework.security.authentication.BadCredentialsException) {
            log.warn("Bad credentials provided for user: {}", username);
        } else if (event.getException() instanceof org.springframework.security.authentication.DisabledException) {
            log.warn("Account disabled for user: {}", username);
        } else if (event.getException() instanceof org.springframework.security.authentication.LockedException) {
            log.warn("Account locked for user: {}", username);
        } else if (event.getException() instanceof org.springframework.security.core.userdetails.UsernameNotFoundException) {
            log.warn("User not found: {}", username);
        }
    }

    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent event) {
        String username = event.getAuthentication() != null ? event.getAuthentication().getName() : "unknown";
        log.info("Logout SUCCESS for user: {}", username);
    }
}
