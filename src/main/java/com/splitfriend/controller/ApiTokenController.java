package com.splitfriend.controller;

import com.splitfriend.model.User;
import com.splitfriend.security.CustomUserDetailsService;
import com.splitfriend.service.ApiTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Profile-page actions for the read-only API token. Kept separate from
 * DashboardController to keep both files small.
 */
@Controller
@RequestMapping("/profile/api-token")
public class ApiTokenController {

    private static final Logger log = LoggerFactory.getLogger(ApiTokenController.class);

    private final ApiTokenService apiTokenService;
    private final MessageSource messageSource;

    public ApiTokenController(ApiTokenService apiTokenService, MessageSource messageSource) {
        this.apiTokenService = apiTokenService;
        this.messageSource = messageSource;
    }

    @PostMapping("/enable")
    public String enable(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        return apply(userDetails.getUser(), redirectAttributes, "enabled", apiTokenService::enable);
    }

    @PostMapping("/regenerate")
    public String regenerate(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        return apply(userDetails.getUser(), redirectAttributes, "regenerated", apiTokenService::regenerate);
    }

    @PostMapping("/disable")
    public String disable(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                          RedirectAttributes redirectAttributes) {
        return apply(userDetails.getUser(), redirectAttributes, "disabled", apiTokenService::disable);
    }

    private String apply(User user,
                         RedirectAttributes redirectAttributes,
                         String messageKeySuffix,
                         java.util.function.UnaryOperator<User> action) {
        try {
            action.apply(user);
            redirectAttributes.addFlashAttribute("message", message("profile.apiToken." + messageKeySuffix));
        } catch (RuntimeException e) {
            log.error("API token action '{}' failed for user {}: {}",
                    messageKeySuffix, user.getEmail(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", message("profile.apiToken.failed"));
        }
        return "redirect:/profile";
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}
