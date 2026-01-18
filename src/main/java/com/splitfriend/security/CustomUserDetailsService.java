package com.splitfriend.security;

import com.splitfriend.model.User;
import com.splitfriend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info("Authentication attempt for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Authentication failed - user not found with email: {}", email);
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        log.info("User found: email={}, role={}, enabled={}, totpEnabled={}",
                user.getEmail(), user.getRole(), user.getEnabled(), user.getTotpEnabled());

        if (user.getEnabled() == null || !user.getEnabled()) {
            log.warn("User account is disabled: {}", email);
        }

        return new CustomUserDetails(user);
    }

    public static class CustomUserDetails implements UserDetails {
        private final User user;

        public CustomUserDetails(User user) {
            this.user = user;
        }

        public User getUser() {
            return user;
        }

        public Long getId() {
            return user.getId();
        }

        public String getName() {
            return user.getName();
        }

        public boolean isTotpEnabled() {
            return user.getTotpEnabled() != null && user.getTotpEnabled();
        }

        public String getTotpSecret() {
            return user.getTotpSecret();
        }

        @Override
        public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        }

        @Override
        public String getPassword() {
            return user.getPasswordHash();
        }

        @Override
        public String getUsername() {
            return user.getEmail();
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return user.getEnabled() != null && user.getEnabled();
        }
    }
}
