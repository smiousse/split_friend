package com.splitfriend.controller;

import com.splitfriend.dto.BalanceDTO;
import com.splitfriend.dto.SettlementDTO;
import com.splitfriend.model.Group;
import com.splitfriend.model.Settlement;
import com.splitfriend.model.User;
import com.splitfriend.security.CustomUserDetailsService;
import com.splitfriend.service.BalanceService;
import com.splitfriend.service.DebtSimplificationService;
import com.splitfriend.service.GroupService;
import com.splitfriend.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/settlements")
public class SettlementController {

    private final SettlementService settlementService;
    private final GroupService groupService;
    private final BalanceService balanceService;
    private final DebtSimplificationService debtSimplificationService;

    public SettlementController(SettlementService settlementService,
                               GroupService groupService,
                               BalanceService balanceService,
                               DebtSimplificationService debtSimplificationService) {
        this.settlementService = settlementService;
        this.groupService = groupService;
        this.balanceService = balanceService;
        this.debtSimplificationService = debtSimplificationService;
    }

    @GetMapping
    public String listSettlements(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                                 @RequestParam("groupId") Long groupId,
                                 Model model) {
        User user = userDetails.getUser();

        if (!groupService.isUserMember(groupId, user.getId())) {
            return "redirect:/groups?error=unauthorized";
        }

        Optional<Group> groupOpt = groupService.findById(groupId);
        if (groupOpt.isEmpty()) {
            return "redirect:/groups?error=notfound";
        }

        Group group = groupOpt.get();
        List<Settlement> settlements = settlementService.findByGroupId(groupId);
        List<BalanceDTO> balances = balanceService.getDetailedBalances(groupId);

        // Calculate simplified debts
        Map<Long, BigDecimal> balanceMap = balanceService.calculateGroupBalances(groupId);
        Map<Long, String> userNames = new HashMap<>();
        for (BalanceDTO balance : balances) {
            userNames.put(balance.getUserId(), balance.getUserName());
        }
        List<BalanceDTO.DebtDTO> simplifiedDebts = debtSimplificationService.simplifyDebts(balanceMap, userNames);

        model.addAttribute("group", group);
        model.addAttribute("settlements", settlements);
        model.addAttribute("balances", balances);
        model.addAttribute("simplifiedDebts", simplifiedDebts);
        model.addAttribute("members", groupService.getGroupMemberUsers(groupId));
        model.addAttribute("currentUserId", user.getId());

        return "settlements/list";
    }

    @PostMapping("/add")
    public String addSettlement(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                               @RequestParam("groupId") Long groupId,
                               @RequestParam("fromUserId") Long fromUserId,
                               @RequestParam("toUserId") Long toUserId,
                               @RequestParam("amount") BigDecimal amount,
                               RedirectAttributes redirectAttributes) {
        User user = userDetails.getUser();

        if (!groupService.isUserMember(groupId, user.getId())) {
            return "redirect:/groups?error=unauthorized";
        }

        Optional<Group> groupOpt = groupService.findById(groupId);
        if (groupOpt.isEmpty()) {
            return "redirect:/groups?error=notfound";
        }

        List<User> members = groupService.getGroupMemberUsers(groupId);
        User fromUser = members.stream().filter(u -> u.getId().equals(fromUserId)).findFirst().orElse(null);
        User toUser = members.stream().filter(u -> u.getId().equals(toUserId)).findFirst().orElse(null);

        if (fromUser == null || toUser == null) {
            redirectAttributes.addFlashAttribute("error", "Invalid users selected");
            return "redirect:/settlements?groupId=" + groupId;
        }

        try {
            settlementService.createSettlement(groupOpt.get(), fromUser, toUser, amount);
            redirectAttributes.addFlashAttribute("message", "Settlement recorded successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/settlements?groupId=" + groupId;
    }

    @PostMapping("/{id}/delete")
    public String deleteSettlement(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                                  @PathVariable Long id,
                                  @RequestParam("groupId") Long groupId,
                                  RedirectAttributes redirectAttributes) {
        User user = userDetails.getUser();

        Optional<Settlement> settlementOpt = settlementService.findById(id);
        if (settlementOpt.isEmpty()) {
            return "redirect:/settlements?groupId=" + groupId + "&error=notfound";
        }

        Settlement settlement = settlementOpt.get();

        // Check authorization
        Optional<Group> groupOpt = groupService.findById(groupId);
        boolean canDelete = settlement.getFromUser().getId().equals(user.getId()) ||
                settlement.getToUser().getId().equals(user.getId()) ||
                (groupOpt.isPresent() && groupOpt.get().getCreatedBy().getId().equals(user.getId()));

        if (!canDelete) {
            redirectAttributes.addFlashAttribute("error", "You cannot delete this settlement");
            return "redirect:/settlements?groupId=" + groupId;
        }

        settlementService.deleteSettlement(id);
        redirectAttributes.addFlashAttribute("message", "Settlement deleted successfully");
        return "redirect:/settlements?groupId=" + groupId;
    }

    @PostMapping("/settle-up")
    public String settleUp(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                          @RequestParam("groupId") Long groupId,
                          @RequestParam("toUserId") Long toUserId,
                          RedirectAttributes redirectAttributes) {
        User user = userDetails.getUser();

        if (!groupService.isUserMember(groupId, user.getId())) {
            return "redirect:/groups?error=unauthorized";
        }

        // Calculate how much the current user owes to the toUser
        Map<Long, BigDecimal> balances = balanceService.calculateGroupBalances(groupId);
        BigDecimal userBalance = balances.getOrDefault(user.getId(), BigDecimal.ZERO);

        if (userBalance.compareTo(BigDecimal.ZERO) >= 0) {
            redirectAttributes.addFlashAttribute("error", "You don't owe anything");
            return "redirect:/settlements?groupId=" + groupId;
        }

        // For simplicity, settle the full amount owed
        BigDecimal amountToSettle = userBalance.abs();

        Optional<Group> groupOpt = groupService.findById(groupId);
        if (groupOpt.isEmpty()) {
            return "redirect:/groups?error=notfound";
        }

        List<User> members = groupService.getGroupMemberUsers(groupId);
        User toUser = members.stream().filter(u -> u.getId().equals(toUserId)).findFirst().orElse(null);

        if (toUser == null) {
            redirectAttributes.addFlashAttribute("error", "Invalid recipient");
            return "redirect:/settlements?groupId=" + groupId;
        }

        try {
            settlementService.createSettlement(groupOpt.get(), user, toUser, amountToSettle);
            redirectAttributes.addFlashAttribute("message", "Settled up successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/settlements?groupId=" + groupId;
    }
}
