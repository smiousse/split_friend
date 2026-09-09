package com.splitfriend.controller;

import com.splitfriend.dto.BudgetForm;
import com.splitfriend.dto.BudgetItemForm;
import com.splitfriend.dto.BudgetSummary;
import com.splitfriend.model.Budget;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.BudgetFrequency;
import com.splitfriend.model.enums.BudgetItemType;
import com.splitfriend.model.enums.BudgetSide;
import com.splitfriend.security.CustomUserDetailsService;
import com.splitfriend.service.BudgetCalculationService;
import com.splitfriend.service.BudgetService;
import com.splitfriend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * The Budget section.
 *
 * Access is enforced in two places, neither of which this class implements:
 * {@link com.splitfriend.config.BudgetEnabledInterceptor} gates the whole
 * {@code /budgets/**} path on the admin-granted flag, and every lookup below
 * goes through {@link BudgetService#requireAccess}, which only returns
 * budgets the caller participates in.
 */
@Controller
@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetCalculationService calculationService;
    private final UserService userService;

    public BudgetController(BudgetService budgetService,
                            BudgetCalculationService calculationService,
                            UserService userService) {
        this.budgetService = budgetService;
        this.calculationService = calculationService;
        this.userService = userService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                       Model model) {
        model.addAttribute("budgets", budgetService.findForUser(userDetails.getId()));
        return "budgets/list";
    }

    @GetMapping("/create")
    public String createForm(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                             Model model) {
        model.addAttribute("budget", new BudgetForm());
        addFormOptions(model, userDetails.getId());
        return "budgets/create";
    }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                         @Valid @ModelAttribute("budget") BudgetForm form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            addFormOptions(model, userDetails.getId());
            return "budgets/create";
        }

        try {
            Budget budget = budgetService.create(form, userDetails.getUser());
            redirectAttributes.addFlashAttribute("message", "Budget created successfully");
            return "redirect:/budgets/" + budget.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            addFormOptions(model, userDetails.getId());
            return "budgets/create";
        }
    }

    @GetMapping("/{id}")
    public String view(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                       @PathVariable Long id,
                       Model model) {
        Budget budget = budgetService.requireAccess(id, userDetails.getId());
        BudgetSummary summary = calculationService.summarize(budget);

        model.addAttribute("budget", budget);
        model.addAttribute("summary", summary);
        model.addAttribute("currentSide", budget.sideOf(userDetails.getId()));
        model.addAttribute("item", new BudgetItemForm());
        addItemOptions(model);
        return "budgets/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                           @PathVariable Long id,
                           Model model) {
        Budget budget = budgetService.requireAccess(id, userDetails.getId());

        BudgetForm form = new BudgetForm();
        form.setName(budget.getName());
        form.setDescription(budget.getDescription());
        form.setCurrency(budget.getCurrency());
        form.setPartnerId(budget.getUserB().getId());
        form.setShareAPercent(budget.getShareAPercent());
        form.setSettlementPeriod(budget.getSettlementPeriod());

        model.addAttribute("budget", form);
        model.addAttribute("budgetId", id);
        model.addAttribute("userAName", budget.getUserA().getName());
        model.addAttribute("userBName", budget.getUserB().getName());
        model.addAttribute("periods", BudgetFrequency.values());
        return "budgets/edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                       @PathVariable Long id,
                       @Valid @ModelAttribute("budget") BudgetForm form,
                       BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please correct the highlighted fields");
            return "redirect:/budgets/" + id + "/edit";
        }

        try {
            budgetService.update(id, form, userDetails.getId());
            redirectAttributes.addFlashAttribute("message", "Budget updated successfully");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/budgets/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        budgetService.delete(id, userDetails.getId());
        redirectAttributes.addFlashAttribute("message", "Budget deleted");
        return "redirect:/budgets";
    }

    // ---------- items ----------

    @PostMapping("/{id}/items")
    public String addItem(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                          @PathVariable Long id,
                          @Valid @ModelAttribute("item") BudgetItemForm form,
                          BindingResult result,
                          RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "A line needs a label and a positive amount");
            return "redirect:/budgets/" + id;
        }

        budgetService.addItem(id, form, userDetails.getId());
        redirectAttributes.addFlashAttribute("message", "Line added");
        return "redirect:/budgets/" + id;
    }

    @PostMapping("/{id}/items/{itemId}")
    public String updateItem(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                             @PathVariable Long id,
                             @PathVariable Long itemId,
                             @Valid @ModelAttribute("item") BudgetItemForm form,
                             BindingResult result,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "A line needs a label and a positive amount");
            return "redirect:/budgets/" + id;
        }

        budgetService.updateItem(id, itemId, form, userDetails.getId());
        redirectAttributes.addFlashAttribute("message", "Line updated");
        return "redirect:/budgets/" + id;
    }

    @PostMapping("/{id}/items/{itemId}/toggle")
    public String toggleItem(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                             @PathVariable Long id,
                             @PathVariable Long itemId,
                             RedirectAttributes redirectAttributes) {
        budgetService.toggleItem(id, itemId, userDetails.getId());
        return "redirect:/budgets/" + id;
    }

    @PostMapping("/{id}/items/{itemId}/delete")
    public String deleteItem(@AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails,
                             @PathVariable Long id,
                             @PathVariable Long itemId,
                             RedirectAttributes redirectAttributes) {
        budgetService.deleteItem(id, itemId, userDetails.getId());
        redirectAttributes.addFlashAttribute("message", "Line removed");
        return "redirect:/budgets/" + id;
    }

    // ---------- helpers ----------

    private void addFormOptions(Model model, Long currentUserId) {
        List<User> candidates = userService.findBudgetEnabledUsers().stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .toList();
        model.addAttribute("candidates", candidates);
        model.addAttribute("periods", BudgetFrequency.values());
    }

    private void addItemOptions(Model model) {
        model.addAttribute("frequencies", BudgetFrequency.values());
        model.addAttribute("itemTypes", BudgetItemType.values());
        model.addAttribute("sides", BudgetSide.values());
    }
}
