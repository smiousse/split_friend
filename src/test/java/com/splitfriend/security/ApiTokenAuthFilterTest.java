package com.splitfriend.security;

import com.splitfriend.model.User;
import com.splitfriend.model.enums.Role;
import com.splitfriend.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiTokenAuthFilterTest {

    private static final String TOKEN = "valid-token";

    private ApiTokenService apiTokenService;
    private ApiTokenAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        apiTokenService = mock(ApiTokenService.class);
        filter = new ApiTokenAuthFilter(apiTokenService);
        request = new MockHttpServletRequest("GET", "/api/v1/balances");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User user() {
        return User.builder().id(7L).email("a@b.c").name("Alice").role(Role.USER).enabled(true).build();
    }

    @Test
    void validTokenAuthenticatesAndContinuesTheChain() throws Exception {
        User user = user();
        when(apiTokenService.authenticate(TOKEN)).thenReturn(Optional.of(user));
        // Capture the context as the chain sees it - the filter clears it afterwards
        Authentication[] seen = new Authentication[1];
        doAnswer(inv -> {
            seen[0] = SecurityContextHolder.getContext().getAuthentication();
            return null;
        }).when(chain).doFilter(any(), any());

        request.addHeader(ApiTokenAuthFilter.TOKEN_HEADER, TOKEN);
        filter.doFilter(request, response, chain);

        assertThat(seen[0]).isNotNull();
        assertThat(seen[0].getPrincipal())
                .isInstanceOf(CustomUserDetailsService.CustomUserDetails.class);
        assertThat(((CustomUserDetailsService.CustomUserDetails) seen[0].getPrincipal()).getUser())
                .isSameAs(user);
        assertThat(seen[0].getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void clearsTheSecurityContextAfterTheRequest() throws Exception {
        when(apiTokenService.authenticate(TOKEN)).thenReturn(Optional.of(user()));
        request.addHeader(ApiTokenAuthFilter.TOKEN_HEADER, TOKEN);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingHeaderIsRejectedWithJsonUnauthorizedAndTheChainIsNotInvoked() throws Exception {
        when(apiTokenService.authenticate(null)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"invalid_token\"}");
        verifyNoInteractions(chain);
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        when(apiTokenService.authenticate("bogus")).thenReturn(Optional.empty());
        request.addHeader(ApiTokenAuthFilter.TOKEN_HEADER, "bogus");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(chain);
    }

    @Test
    void readsTheTokenFromTheXApiTokenHeader() throws Exception {
        when(apiTokenService.authenticate(TOKEN)).thenReturn(Optional.of(user()));
        request.addHeader(ApiTokenAuthFilter.TOKEN_HEADER, TOKEN);

        filter.doFilter(request, response, chain);

        verify(apiTokenService).authenticate(TOKEN);
        verify(chain).doFilter(request, response);
    }
}
