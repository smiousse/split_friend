package com.splitfriend.controller;

import com.splitfriend.dto.UserDTO;
import com.splitfriend.model.User;
import com.splitfriend.security.CustomUserDetailsService;
import com.splitfriend.service.TotpService;
import com.splitfriend.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;
    private final TotpService totpService;

    public AuthController(UserService userService, TotpService totpService) {
        this.userService = userService;
        this.totpService = totpService;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                           @RequestParam(value = "logout", required = false) String logout,
                           Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid email or password");
        }
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully");
        }
        return "auth/login";
    }

    @GetMapping("/setup-2fa")
    public String setup2faPage(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                               HttpSession session,
                               Model model) {
        User user = userDetails.getUser();

        if (user.getTotpEnabled() != null && user.getTotpEnabled()) {
            return "redirect:/dashboard";
        }

        String secret = (String) session.getAttribute("totp_setup_secret");
        if (secret == null) {
            secret = totpService.generateSecret();
            session.setAttribute("totp_setup_secret", secret);
        }

        String qrCodeDataUri = totpService.generateQrCodeDataUri(secret, user.getEmail());

        model.addAttribute("secret", secret);
        model.addAttribute("qrCode", qrCodeDataUri);
        return "auth/setup-2fa";
    }

    @PostMapping("/setup-2fa")
    public String enable2fa(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                           @RequestParam("code") String code,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        String secret = (String) session.getAttribute("totp_setup_secret");

        if (secret == null) {
            redirectAttributes.addFlashAttribute("error", "Setup session expired. Please try again.");
            return "redirect:/setup-2fa";
        }

        if (!totpService.verifyCode(secret, code)) {
            redirectAttributes.addFlashAttribute("error", "Invalid verification code. Please try again.");
            return "redirect:/setup-2fa";
        }

        User user = userDetails.getUser();
        userService.setTotpSecret(user, secret);
        userService.enableTotp(user);
        session.removeAttribute("totp_setup_secret");

        redirectAttributes.addFlashAttribute("message", "Two-factor authentication enabled successfully!");
        return "redirect:/dashboard";
    }

    @GetMapping("/verify-2fa")
    public String verify2faPage() {
        return "auth/verify-2fa";
    }

    @PostMapping("/verify-2fa")
    public String verify2fa(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                           @RequestParam("code") String code,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        User user = userDetails.getUser();

        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            redirectAttributes.addFlashAttribute("error", "Invalid verification code");
            return "redirect:/verify-2fa";
        }

        session.setAttribute("2fa_verified", true);
        return "redirect:/dashboard";
    }

    @PostMapping("/disable-2fa")
    public String disable2fa(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                            @RequestParam("code") String code,
                            RedirectAttributes redirectAttributes) {
        User user = userDetails.getUser();

        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            redirectAttributes.addFlashAttribute("error", "Invalid verification code");
            return "redirect:/profile";
        }

        userService.disableTotp(user);
        redirectAttributes.addFlashAttribute("message", "Two-factor authentication disabled");
        return "redirect:/profile";
    }
}
