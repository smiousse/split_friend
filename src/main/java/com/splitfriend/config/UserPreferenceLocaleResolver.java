package com.splitfriend.config;

import com.splitfriend.model.User;
import com.splitfriend.repository.UserRepository;
import com.splitfriend.security.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

@Component
public class UserPreferenceLocaleResolver implements LocaleResolver {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final String SESSION_LOCALE_ATTRIBUTE = SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME;

    private final UserRepository userRepository;

    public UserPreferenceLocaleResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // 1. Check if user is authenticated
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetailsService.CustomUserDetails) {
            CustomUserDetailsService.CustomUserDetails userDetails =
                    (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();
            if (user != null && user.getLanguage() != null) {
                return Locale.forLanguageTag(user.getLanguage());
            }
        }

        // 2. Check session
        Object sessionLocale = request.getSession().getAttribute(SESSION_LOCALE_ATTRIBUTE);
        if (sessionLocale instanceof Locale) {
            return (Locale) sessionLocale;
        }

        // 3. Check Accept-Language header
        Locale requestLocale = request.getLocale();
        if (requestLocale != null && isSupportedLocale(requestLocale)) {
            return requestLocale;
        }

        // 4. Default to English
        return DEFAULT_LOCALE;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // Store in session for anonymous users
        request.getSession().setAttribute(SESSION_LOCALE_ATTRIBUTE, locale);
    }

    private boolean isSupportedLocale(Locale locale) {
        String language = locale.getLanguage();
        return "en".equals(language) || "fr".equals(language);
    }
}
