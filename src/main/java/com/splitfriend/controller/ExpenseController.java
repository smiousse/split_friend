package com.splitfriend.controller;

import com.splitfriend.dto.ExpenseDTO;
import com.splitfriend.model.*;
import com.splitfriend.model.enums.SplitType;
import com.splitfriend.security.CustomUserDetailsService;
import com.splitfriend.service.ExpenseService;
import com.splitfriend.service.ExportService;
import com.splitfriend.service.GroupService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final GroupService groupService;
    private final ExportService exportService;

    public ExpenseController(ExpenseService expenseService,
                            GroupService groupService,
                            ExportService exportService) {
        this.expenseService = expenseService;
        this.groupService = groupService;
        this.exportService = exportService;
    }

    @GetMapping("/add")
    public String addExpenseForm(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                                @RequestParam("groupId") Long groupId,
                                Model model) {
        User user = userDetails.getUser();

        if (!groupService.isUserMember(groupId, user.getId())) {
            return "redirect:/groups?error=unauthorized";
        }

        Optional<Group> groupOpt = groupService.findByIdWithMembers(groupId);
        if (groupOpt.isEmpty()) {
            return "redirect:/groups?error=notfound";
        }

        Group group = groupOpt.get();
        List<User> members = groupService.getGroupMemberUsers(groupId);

        ExpenseDTO expenseDTO = ExpenseDTO.builder()
                .groupId(groupId)
                .paidById(user.getId())
                .splitType(SplitType.EQUAL)
                .expenseDate(LocalDate.now())
                .build();

        model.addAttribute("expense", expenseDTO);
        model.addAttribute("group", group);
        model.addAttribute("members", members);
        model.addAttribute("splitTypes", SplitType.values());

        return "expenses/add";
    }

    @PostMapping("/add")
    public String addExpense(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                            @Valid @ModelAttribute("expense") ExpenseDTO expenseDTO,
                            BindingResult result,
                            @RequestParam(value = "bill", required = false) MultipartFile bill,
                            @RequestParam(value = "participantIds", required = false) List<Long> participantIds,
                            @RequestParam Map<String, String> allParams,
                            RedirectAttributes redirectAttributes,
                            Model model) {

        User user = userDetails.getUser();
        Long groupId = expenseDTO.getGroupId();

        if (!groupService.isUserMember(groupId, user.getId())) {
            return "redirect:/groups?error=unauthorized";
        }

        if (result.hasErrors()) {
            Optional<Group> groupOpt = groupService.findByIdWithMembers(groupId);
            groupOpt.ifPresent(group -> {
                model.addAttribute("group", group);
                model.addAttribute("members", groupService.getGroupMemberUsers(groupId));
            });
            model.addAttribute("splitTypes", SplitType.values());
            return "expenses/add";
        }

        Optional<Group> groupOpt = groupService.findById(groupId);
        if (groupOpt.isEmpty()) {
            return "redirect:/groups?error=notfound";
        }

        Group group = groupOpt.get();

        // Get the payer
        User payer = groupService.getGroupMemberUsers(groupId).stream()
                .filter(u -> u.getId().equals(expenseDTO.getPaidById()))
                .findFirst()
                .orElse(user);

        // Get participants
        List<User> participants;
        if (participantIds == null || participantIds.isEmpty()) {
            participants = groupService.getGroupMemberUsers(groupId);
        } else {
            List<User> allMembers = groupService.getGroupMemberUsers(groupId);
            participants = allMembers.stream()
                    .filter(u -> participantIds.contains(u.getId()))
                    .collect(Collectors.toList());
        }

        // Parse split values from form
        Map<Long, BigDecimal> exactAmounts = new HashMap<>();
        Map<Long, BigDecimal> percentages = new HashMap<>();
        Map<Long, Integer> shares = new HashMap<>();

        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.isEmpty()) continue;

            try {
                if (key.startsWith("exactAmount_")) {
                    Long userId = Long.parseLong(key.substring("exactAmount_".length()));
                    exactAmounts.put(userId, new BigDecimal(value));
                } else if (key.startsWith("percentage_")) {
                    Long userId = Long.parseLong(key.substring("percentage_".length()));
                    percentages.put(userId, new BigDecimal(value));
                } else if (key.startsWith("shares_")) {
                    Long userId = Long.parseLong(key.substring("shares_".length()));
                    shares.put(userId, Integer.parseInt(value));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            expenseService.createExpense(
                    group,
                    payer,
                    expenseDTO.getDescription(),
                    expenseDTO.getAmount(),
                    expenseDTO.getSplitType(),
                    expenseDTO.getExpenseDate(),
                    exactAmounts,
                    percentages,
                    shares,
                    participants,
                    bill
            );

            redirectAttributes.addFlashAttribute("message", "Expense added successfully!");
            return "redirect:/groups/" + groupId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to add expense: " + e.getMessage());
            return "redirect:/expenses/add?groupId=" + groupId;
        }
    }

    @GetMapping("/{id}")
    public String viewExpense(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                             @PathVariable Long id,
                             Model model) {
        User user = userDetails.getUser();

        Optional<Expense> expenseOpt = expenseService.findByIdWithSplits(id);
        if (expenseOpt.isEmpty()) {
            return "redirect:/dashboard?error=notfound";
        }

        Expense expense = expenseOpt.get();

        if (!groupService.isUserMember(expense.getGroup().getId(), user.getId())) {
            return "redirect:/groups?error=unauthorized";
        }

        model.addAttribute("expense", expense);
        return "expenses/view";
    }

    @PostMapping("/{id}/delete")
    public String deleteExpense(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                               @PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        User user = userDetails.getUser();

        Optional<Expense> expenseOpt = expenseService.findById(id);
        if (expenseOpt.isEmpty()) {
            return "redirect:/dashboard?error=notfound";
        }

        Expense expense = expenseOpt.get();
        Long groupId = expense.getGroup().getId();

        // Check if user is the payer or group creator
        Optional<Group> groupOpt = groupService.findById(groupId);
        boolean canDelete = expense.getPaidBy().getId().equals(user.getId()) ||
                (groupOpt.isPresent() && groupOpt.get().getCreatedBy().getId().equals(user.getId()));

        if (!canDelete) {
            redirectAttributes.addFlashAttribute("error", "You cannot delete this expense");
            return "redirect:/groups/" + groupId;
        }

        expenseService.deleteExpense(id);
        redirectAttributes.addFlashAttribute("message", "Expense deleted successfully");
        return "redirect:/groups/" + groupId;
    }

    @GetMapping("/export")
    public void exportExpenses(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                              @RequestParam("groupId") Long groupId,
                              HttpServletResponse response) throws IOException {
        User user = userDetails.getUser();

        if (!groupService.isUserMember(groupId, user.getId())) {
            response.sendError(403, "Unauthorized");
            return;
        }

        List<Expense> expenses = expenseService.findByGroupIdWithSplits(groupId);
        String csv = exportService.exportExpensesToCsv(expenses);

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"expenses_group_" + groupId + ".csv\"");
        response.getWriter().write(csv);
    }
}
