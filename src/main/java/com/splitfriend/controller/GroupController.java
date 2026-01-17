package com.splitfriend.controller;

import com.splitfriend.dto.BalanceDTO;
import com.splitfriend.dto.GroupDTO;
import com.splitfriend.model.Group;
import com.splitfriend.model.GroupMember;
import com.splitfriend.model.User;
import com.splitfriend.security.CustomUserDetailsService;
import com.splitfriend.service.BalanceService;
import com.splitfriend.service.ExpenseService;
import com.splitfriend.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/groups")
public class GroupController {

    private final GroupService groupService;
    private final BalanceService balanceService;
    private final ExpenseService expenseService;

    public GroupController(GroupService groupService,
                          BalanceService balanceService,
                          ExpenseService expenseService) {
        this.groupService = groupService;
        this.balanceService = balanceService;
        this.expenseService = expenseService;
    }

    @GetMapping
    public String listGroups(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                            Model model) {
        User user = userDetails.getUser();
        List<Group> groups = groupService.findByUser(user);
        model.addAttribute("groups", groups);
        return "groups/list";
    }

    @GetMapping("/create")
    public String createGroupForm(Model model) {
        model.addAttribute("group", new GroupDTO());
        return "groups/create";
    }

    @PostMapping("/create")
    public String createGroup(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                             @Valid @ModelAttribute("group") GroupDTO groupDTO,
                             BindingResult result,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "groups/create";
        }

        User user = userDetails.getUser();
        Group group = groupService.createGroup(
                groupDTO.getName(),
                groupDTO.getDescription(),
                groupDTO.getCurrency(),
                user
        );

        redirectAttributes.addFlashAttribute("message", "Group created successfully!");
        return "redirect:/groups/" + group.getId();
    }

    @GetMapping("/{id}")
    public String viewGroup(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                           @PathVariable Long id,
                           Model model) {
        User user = userDetails.getUser();

        Optional<Group> groupOpt = groupService.findByIdWithMembers(id);
        if (groupOpt.isEmpty()) {
            return "redirect:/groups?error=notfound";
        }

        Group group = groupOpt.get();

        if (!groupService.isUserMember(id, user.getId())) {
            return "redirect:/groups?error=unauthorized";
        }

        List<GroupMember> members = groupService.getGroupMembers(id);
        List<BalanceDTO> balances = balanceService.getDetailedBalances(id);
        List<BalanceDTO.DebtDTO> debts = balanceService.calculateDebts(id);
        BigDecimal totalExpenses = expenseService.getTotalExpensesByGroup(id);

        model.addAttribute("group", group);
        model.addAttribute("members", members);
        model.addAttribute("balances", balances);
        model.addAttribute("debts", debts);
        model.addAttribute("totalExpenses", totalExpenses);
        model.addAttribute("currentUserId", user.getId());

        return "groups/view";
    }

    @GetMapping("/{id}/edit")
    public String editGroupForm(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                               @PathVariable Long id,
                               Model model) {
        Optional<Group> groupOpt = groupService.findById(id);
        if (groupOpt.isEmpty()) {
            return "redirect:/groups?error=notfound";
        }

        Group group = groupOpt.get();
        User user = userDetails.getUser();

        if (!group.getCreatedBy().getId().equals(user.getId())) {
            return "redirect:/groups/" + id + "?error=unauthorized";
        }

        GroupDTO groupDTO = GroupDTO.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .currency(group.getCurrency())
                .build();

        model.addAttribute("group", groupDTO);
        return "groups/edit";
    }

    @PostMapping("/{id}/edit")
    public String updateGroup(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                             @PathVariable Long id,
                             @Valid @ModelAttribute("group") GroupDTO groupDTO,
                             BindingResult result,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "groups/edit";
        }

        Optional<Group> groupOpt = groupService.findById(id);
        if (groupOpt.isEmpty()) {
            return "redirect:/groups?error=notfound";
        }

        Group group = groupOpt.get();
        User user = userDetails.getUser();

        if (!group.getCreatedBy().getId().equals(user.getId())) {
            return "redirect:/groups/" + id + "?error=unauthorized";
        }

        group.setName(groupDTO.getName());
        group.setDescription(groupDTO.getDescription());
        group.setCurrency(groupDTO.getCurrency());
        groupService.updateGroup(group);

        redirectAttributes.addFlashAttribute("message", "Group updated successfully!");
        return "redirect:/groups/" + id;
    }

    @PostMapping("/{id}/add-member")
    public String addMember(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                           @PathVariable Long id,
                           @RequestParam("email") String email,
                           RedirectAttributes redirectAttributes) {
        try {
            groupService.addMemberByEmail(id, email);
            redirectAttributes.addFlashAttribute("message", "Member added successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/groups/" + id;
    }

    @PostMapping("/{id}/leave")
    public String leaveGroup(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                            @PathVariable Long id,
                            RedirectAttributes redirectAttributes) {
        User user = userDetails.getUser();

        Optional<Group> groupOpt = groupService.findById(id);
        if (groupOpt.isEmpty()) {
            return "redirect:/groups?error=notfound";
        }

        Group group = groupOpt.get();

        // Check if user is the creator
        if (group.getCreatedBy().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("error", "Group creator cannot leave. Please delete the group instead.");
            return "redirect:/groups/" + id;
        }

        groupService.removeMember(id, user.getId());
        redirectAttributes.addFlashAttribute("message", "You have left the group");
        return "redirect:/groups";
    }

    @PostMapping("/{id}/remove-member/{userId}")
    public String removeMember(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                              @PathVariable Long id,
                              @PathVariable Long userId,
                              RedirectAttributes redirectAttributes) {
        User currentUser = userDetails.getUser();

        Optional<Group> groupOpt = groupService.findById(id);
        if (groupOpt.isEmpty()) {
            return "redirect:/groups?error=notfound";
        }

        Group group = groupOpt.get();

        // Only creator can remove members
        if (!group.getCreatedBy().getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("error", "Only group creator can remove members");
            return "redirect:/groups/" + id;
        }

        // Cannot remove creator
        if (group.getCreatedBy().getId().equals(userId)) {
            redirectAttributes.addFlashAttribute("error", "Cannot remove group creator");
            return "redirect:/groups/" + id;
        }

        groupService.removeMember(id, userId);
        redirectAttributes.addFlashAttribute("message", "Member removed successfully");
        return "redirect:/groups/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteGroup(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                             @PathVariable Long id,
                             RedirectAttributes redirectAttributes) {
        User user = userDetails.getUser();

        Optional<Group> groupOpt = groupService.findById(id);
        if (groupOpt.isEmpty()) {
            return "redirect:/groups?error=notfound";
        }

        Group group = groupOpt.get();

        if (!group.getCreatedBy().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("error", "Only group creator can delete the group");
            return "redirect:/groups/" + id;
        }

        groupService.deleteGroup(id);
        redirectAttributes.addFlashAttribute("message", "Group deleted successfully");
        return "redirect:/groups";
    }
}
