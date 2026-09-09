package com.splitfriend.config;

import com.splitfriend.model.User;
import com.splitfriend.security.SecurityUtils;
import com.splitfriend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Refuses every {@code /budgets/**} request from a user who has not been
 * granted the feature.
 *
 * Registered in {@link WebConfig}. Admins pass through so they can support the
 * feature without granting it to themselves.
 */
@Component
public class BudgetEnabledInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public BudgetEnabledInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Optional<User> current = SecurityUtils.getCurrentUser();
        if (current.isEmpty()) {
            throw new AccessDeniedException("Authentication required for the budget feature");
        }
        if (SecurityUtils.isCurrentUserAdmin() || userService.isBudgetEnabled(current.get().getId())) {
            return true;
        }
        throw new AccessDeniedException("The budget feature is not enabled for this account");
    }
}
