package com.splitfriend.controller.admin;

import com.splitfriend.model.User;
import com.splitfriend.model.enums.Role;
import com.splitfriend.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

    private final UserService userService;

    public UserManagementController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String listUsers(@RequestParam(value = "search", required = false) String search,
                           Model model) {
        List<User> users;
        if (search != null && !search.isEmpty()) {
            users = userService.searchUsers(search);
            model.addAttribute("search", search);
        } else {
            users = userService.findAllUsers();
        }
        model.addAttribute("users", users);
        model.addAttribute("roles", Role.values());
        return "admin/users";
    }

    @GetMapping("/create")
    public String createUserForm(Model model) {
        model.addAttribute("roles", Role.values());
        return "admin/user-create";
    }

    @PostMapping("/create")
    public String createUser(@RequestParam("email") String email,
                            @RequestParam("name") String name,
                            @RequestParam("password") String password,
                            @RequestParam("role") Role role,
                            RedirectAttributes redirectAttributes) {
        if (email == null || email.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Email is required");
            return "redirect:/admin/users/create";
        }

        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Name is required");
            return "redirect:/admin/users/create";
        }

        if (password == null || password.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters");
            return "redirect:/admin/users/create";
        }

        if (userService.existsByEmail(email)) {
            redirectAttributes.addFlashAttribute("error", "Email is already registered");
            return "redirect:/admin/users/create";
        }

        try {
            User user = userService.registerUser(email, password, name);
            user.setRole(role);
            userService.updateUser(user);
            redirectAttributes.addFlashAttribute("message", "User created successfully");
            return "redirect:/admin/users";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create user: " + e.getMessage());
            return "redirect:/admin/users/create";
        }
    }

    @GetMapping("/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        Optional<User> userOpt = userService.findById(id);
        if (userOpt.isEmpty()) {
            return "redirect:/admin/users?error=notfound";
        }
        model.addAttribute("user", userOpt.get());
        model.addAttribute("roles", Role.values());
        return "admin/user-detail";
    }

    @PostMapping("/{id}/enable")
    public String enableUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.enableUser(id);
        redirectAttributes.addFlashAttribute("message", "User enabled successfully");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/disable")
    public String disableUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.disableUser(id);
        redirectAttributes.addFlashAttribute("message", "User disabled successfully");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userService.findById(id);
        if (userOpt.isPresent() && userOpt.get().getRole() == Role.ADMIN) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete admin users");
            return "redirect:/admin/users";
        }

        userService.deleteUser(id);
        redirectAttributes.addFlashAttribute("message", "User deleted successfully");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/role")
    public String updateRole(@PathVariable Long id,
                            @RequestParam("role") Role role,
                            RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userService.findById(id);
        if (userOpt.isEmpty()) {
            return "redirect:/admin/users?error=notfound";
        }

        User user = userOpt.get();
        user.setRole(role);
        userService.updateUser(user);

        redirectAttributes.addFlashAttribute("message", "User role updated successfully");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id,
                               @RequestParam("newPassword") String newPassword,
                               RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userService.findById(id);
        if (userOpt.isEmpty()) {
            return "redirect:/admin/users?error=notfound";
        }

        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters");
            return "redirect:/admin/users/" + id;
        }

        userService.updatePassword(userOpt.get(), newPassword);
        redirectAttributes.addFlashAttribute("message", "Password reset successfully");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/disable-2fa")
    public String disableUserTotp(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userService.findById(id);
        if (userOpt.isEmpty()) {
            return "redirect:/admin/users?error=notfound";
        }

        userService.disableTotp(userOpt.get());
        redirectAttributes.addFlashAttribute("message", "2FA disabled for user");
        return "redirect:/admin/users/" + id;
    }
}
