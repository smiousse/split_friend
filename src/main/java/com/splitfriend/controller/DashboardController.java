package com.splitfriend.controller;

import com.splitfriend.dto.BalanceDTO;
import com.splitfriend.model.Group;
import com.splitfriend.model.User;
import com.splitfriend.security.CustomUserDetailsService;
import com.splitfriend.service.BalanceService;
import com.splitfriend.service.GroupService;
import com.splitfriend.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private final GroupService groupService;
    private final BalanceService balanceService;
    private final UserService userService;

    public DashboardController(GroupService groupService,
                               BalanceService balanceService,
                               UserService userService) {
        this.groupService = groupService;
        this.balanceService = balanceService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                           Model model) {
        User user = userDetails.getUser();
        List<Group> groups = groupService.findByUser(user);

        // Calculate balances for each group
        Map<Long, BigDecimal> groupBalances = new HashMap<>();
        BigDecimal totalOwed = BigDecimal.ZERO;
        BigDecimal totalOwing = BigDecimal.ZERO;

        for (Group group : groups) {
            BigDecimal balance = balanceService.getUserBalanceInGroup(group.getId(), user.getId());
            groupBalances.put(group.getId(), balance);

            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                totalOwed = totalOwed.add(balance);
            } else {
                totalOwing = totalOwing.add(balance.abs());
            }
        }

        model.addAttribute("user", user);
        model.addAttribute("groups", groups);
        model.addAttribute("groupBalances", groupBalances);
        model.addAttribute("totalOwed", totalOwed);
        model.addAttribute("totalOwing", totalOwing);
        model.addAttribute("netBalance", totalOwed.subtract(totalOwing));

        return "dashboard";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                         Model model) {
        User user = userDetails.getUser();
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                               @RequestParam("name") String name,
                               RedirectAttributes redirectAttributes) {
        User user = userDetails.getUser();
        user.setName(name);
        userService.updateUser(user);
        redirectAttributes.addFlashAttribute("message", "Profile updated successfully");
        return "redirect:/profile";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                                @RequestParam("currentPassword") String currentPassword,
                                @RequestParam("newPassword") String newPassword,
                                @RequestParam("confirmPassword") String confirmPassword,
                                RedirectAttributes redirectAttributes) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match");
            return "redirect:/profile";
        }

        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters");
            return "redirect:/profile";
        }

        User user = userDetails.getUser();
        userService.updatePassword(user, newPassword);
        redirectAttributes.addFlashAttribute("message", "Password changed successfully");
        return "redirect:/profile";
    }
}
