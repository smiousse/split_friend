package com.splitfriend.controller.advice;

import com.splitfriend.security.CustomUserDetailsService;
import com.splitfriend.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Publishes {@code budgetEnabled} to every view so the navbar can show or hide
 * the Budget menu.
 *
 * The flag is re-read from the database on each request rather than taken from
 * the authenticated principal. The principal wraps a {@code User} snapshot
 * captured at login and remember-me tokens live for 30 days, so a principal
 * read would keep the menu visible for weeks after an admin revoked access.
 *
 * This is presentation only. {@link com.splitfriend.config.BudgetEnabledInterceptor}
 * enforces the same flag on the routes themselves - hiding a link is not
 * access control.
 */
@ControllerAdvice
public class BudgetMenuAdvice {

    private final UserService userService;

    public BudgetMenuAdvice(UserService userService) {
        this.userService = userService;
    }

    @ModelAttribute("budgetEnabled")
    public boolean budgetEnabled(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails) {
        if (userDetails == null) {
            return false;
        }
        return userService.isBudgetEnabled(userDetails.getId());
    }
}
