package com.splitfriend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Renders the access-denied page for any HTTP method.
 *
 * Spring Security <em>forwards</em> the original request here rather than
 * redirecting, so a denied POST arrives as a POST. Registering this path as a
 * plain view controller would answer those with 405 Method Not Allowed and a
 * blank page, which reads like a broken form rather than a refusal.
 */
@Controller
@RequestMapping("/access-denied")
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessDeniedController {

    @RequestMapping
    public String accessDenied() {
        return "error/access-denied";
    }
}
