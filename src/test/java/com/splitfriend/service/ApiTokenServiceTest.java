package com.splitfriend.service;

import com.splitfriend.model.User;
import com.splitfriend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiTokenServiceTest {

    private UserRepository userRepository;
    private ApiTokenService apiTokenService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        apiTokenService = new ApiTokenService(userRepository);
        when(userRepository.findByApiToken(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User user() {
        return User.builder().id(1L).email("a@b.c").name("Alice").enabled(true).build();
    }

    @Test
    void generatesUrlSafeTokensOf256Bits() {
        String token = apiTokenService.generateToken();

        // 32 random bytes, Base64url without padding
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void generatesDistinctTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(apiTokenService.generateToken());
        }
        assertThat(tokens).hasSize(100);
    }

    @Test
    void retriesWhenGeneratedTokenAlreadyExists() {
        when(userRepository.findByApiToken(anyString()))
                .thenReturn(Optional.of(user()))
                .thenReturn(Optional.empty());

        assertThat(apiTokenService.generateToken()).isNotBlank();
        verify(userRepository, org.mockito.Mockito.times(2)).findByApiToken(anyString());
    }

    @Test
    void enableIssuesTokenAndSetsFlagAndTimestamp() {
        User user = user();

        apiTokenService.enable(user);

        assertThat(user.getApiTokenEnabled()).isTrue();
        assertThat(user.getApiToken()).isNotBlank();
        assertThat(user.getApiTokenCreatedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void enableKeepsExistingTokenWhenAlreadyEnabled() {
        User user = user();
        user.setApiTokenEnabled(true);
        user.setApiToken("existing-token");

        apiTokenService.enable(user);

        assertThat(user.getApiToken()).isEqualTo("existing-token");
        verifyNoInteractions(userRepository);
    }

    @Test
    void regenerateReplacesTheToken() {
        User user = user();
        apiTokenService.enable(user);
        String first = user.getApiToken();

        apiTokenService.regenerate(user);

        assertThat(user.getApiToken()).isNotEqualTo(first);
        assertThat(user.getApiTokenEnabled()).isTrue();
    }

    @Test
    void disableWipesTheToken() {
        User user = user();
        apiTokenService.enable(user);

        apiTokenService.disable(user);

        assertThat(user.getApiTokenEnabled()).isFalse();
        assertThat(user.getApiToken()).isNull();
        assertThat(user.getApiTokenCreatedAt()).isNull();
    }

    @Test
    void authenticateResolvesAnEnabledToken() {
        User user = user();
        user.setApiTokenEnabled(true);
        user.setApiToken("token");
        when(userRepository.findByApiToken("token")).thenReturn(Optional.of(user));

        assertThat(apiTokenService.authenticate("token")).contains(user);
    }

    @Test
    void authenticateShortCircuitsOnNullOrBlankTokenWithoutQueryingTheDatabase() {
        assertThat(apiTokenService.authenticate(null)).isEmpty();
        assertThat(apiTokenService.authenticate("   ")).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void authenticateRejectsUnknownToken() {
        assertThat(apiTokenService.authenticate("unknown")).isEmpty();
    }

    @Test
    void authenticateRejectsTokenWhoseApiAccessIsDisabled() {
        User user = user();
        user.setApiTokenEnabled(false);
        user.setApiToken("token");
        when(userRepository.findByApiToken("token")).thenReturn(Optional.of(user));

        assertThat(apiTokenService.authenticate("token")).isEmpty();
    }

    @Test
    void authenticateRejectsDisabledAccount() {
        User user = user();
        user.setEnabled(false);
        user.setApiTokenEnabled(true);
        user.setApiToken("token");
        when(userRepository.findByApiToken("token")).thenReturn(Optional.of(user));

        assertThat(apiTokenService.authenticate("token")).isEmpty();
    }
}
