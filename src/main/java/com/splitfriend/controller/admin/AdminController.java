package com.splitfriend.controller.admin;

import com.splitfriend.service.ExpenseService;
import com.splitfriend.service.GroupService;
import com.splitfriend.service.SettlementService;
import com.splitfriend.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final SettlementService settlementService;

    public AdminController(UserService userService,
                          GroupService groupService,
                          ExpenseService expenseService,
                          SettlementService settlementService) {
        this.userService = userService;
        this.groupService = groupService;
        this.expenseService = expenseService;
        this.settlementService = settlementService;
    }

    @GetMapping
    public String dashboard(Model model) {
        // Statistics
        long totalUsers = userService.countTotalUsers();
        long activeUsers = userService.countActiveUsers();
        long totalGroups = groupService.countGroups();
        long totalExpenses = expenseService.countExpenses();
        BigDecimal totalExpensesAmount = expenseService.getTotalExpensesAmount();
        long totalSettlements = settlementService.countSettlements();
        BigDecimal totalSettledAmount = settlementService.getTotalSettledAmount();

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("totalGroups", totalGroups);
        model.addAttribute("totalExpenses", totalExpenses);
        model.addAttribute("totalExpensesAmount", totalExpensesAmount != null ? totalExpensesAmount : BigDecimal.ZERO);
        model.addAttribute("totalSettlements", totalSettlements);
        model.addAttribute("totalSettledAmount", totalSettledAmount != null ? totalSettledAmount : BigDecimal.ZERO);

        return "admin/dashboard";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        return "admin/settings";
    }
}
