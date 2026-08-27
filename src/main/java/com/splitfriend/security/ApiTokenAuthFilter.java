package com.splitfriend.security;

import com.splitfriend.model.User;
import com.splitfriend.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates requests to the read-only balance API using the
 * {@code X-API-Token} header. Rejected requests get a JSON 401 rather than the
 * form-login redirect the session-based chain would produce.
 *
 * Deliberately not a Spring bean: Boot auto-registers Filter beans against every
 * request, which would reject the whole session-based application. SecurityConfig
 * constructs it and scopes it to the API chain.
 */
public class ApiTokenAuthFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-API-Token";

    private static final Logger log = LoggerFactory.getLogger(ApiTokenAuthFilter.class);
    private static final String UNAUTHORIZED_BODY = "{\"error\":\"invalid_token\"}";

    private final ApiTokenService apiTokenService;

    public ApiTokenAuthFilter(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<User> user = apiTokenService.authenticate(request.getHeader(TOKEN_HEADER));

        if (user.isEmpty()) {
            log.warn("Rejected API request to {} - missing or invalid {} header",
                    request.getRequestURI(), TOKEN_HEADER);
            writeUnauthorized(response);
            return;
        }

        CustomUserDetailsService.CustomUserDetails principal =
                new CustomUserDetailsService.CustomUserDetails(user.get());

        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
