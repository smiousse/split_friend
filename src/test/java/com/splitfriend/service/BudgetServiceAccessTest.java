package com.splitfriend.service;

import com.splitfriend.dto.BudgetItemForm;
import com.splitfriend.model.Budget;
import com.splitfriend.model.BudgetItem;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.BudgetFrequency;
import com.splitfriend.model.enums.BudgetSide;
import com.splitfriend.repository.BudgetItemRepository;
import com.splitfriend.repository.BudgetRepository;
import com.splitfriend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetServiceAccessTest {

    private static final Long AUDREY = 1L;
    private static final Long STEPHANE = 2L;
    private static final Long OUTSIDER = 99L;
    private static final Long BUDGET_ID = 10L;

    private BudgetRepository budgetRepository;
    private BudgetItemRepository budgetItemRepository;
    private BudgetService service;

    @BeforeEach
    void setUp() {
        budgetRepository = mock(BudgetRepository.class);
        budgetItemRepository = mock(BudgetItemRepository.class);
        service = new BudgetService(budgetRepository, budgetItemRepository, mock(UserRepository.class));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a participant can load their own budget")
    void participantMayLoadBudget() {
        when(budgetRepository.findByIdForParticipant(BUDGET_ID, STEPHANE)).thenReturn(Optional.of(budget()));

        assertThat(service.requireAccess(BUDGET_ID, STEPHANE).getId()).isEqualTo(BUDGET_ID);
    }

    @Test
    @DisplayName("a non-participant is refused - the scoped query returns nothing")
    void outsiderIsRefused() {
        when(budgetRepository.findByIdForParticipant(BUDGET_ID, OUTSIDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireAccess(BUDGET_ID, OUTSIDER))
                .isInstanceOf(AccessDeniedException.class);

        // The unscoped lookup must never be reached for a non-admin.
        verify(budgetRepository, never()).findByIdWithParticipants(anyLong());
    }

    @Test
    @DisplayName("an item from another budget cannot be edited by pairing it with an owned budget id")
    void rejectsItemBelongingToAnotherBudget() {
        when(budgetRepository.findByIdForParticipant(BUDGET_ID, STEPHANE)).thenReturn(Optional.of(budget()));

        Budget otherBudget = Budget.builder().id(777L).items(new ArrayList<>()).build();
        BudgetItem foreignItem = BudgetItem.builder().id(5L).budget(otherBudget).build();
        when(budgetItemRepository.findById(5L)).thenReturn(Optional.of(foreignItem));

        assertThatThrownBy(() -> service.deleteItem(BUDGET_ID, 5L, STEPHANE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not belong to budget");

        verify(budgetItemRepository, never()).delete(foreignItem);
    }

    @Test
    void newItemsAreAppendedAfterTheExistingOnes() {
        when(budgetRepository.findByIdForParticipant(BUDGET_ID, STEPHANE)).thenReturn(Optional.of(budget()));
        when(budgetItemRepository.findMaxSortOrder(BUDGET_ID)).thenReturn(4);
        when(budgetItemRepository.save(org.mockito.ArgumentMatchers.any(BudgetItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BudgetItemForm form = new BudgetItemForm();
        form.setLabel("Internet");
        form.setAmount(new BigDecimal("117.22"));
        form.setFrequency(BudgetFrequency.MONTHLY);
        form.setPaidBySide(BudgetSide.B);
        form.setActive(true);

        assertThat(service.addItem(BUDGET_ID, form, STEPHANE).getSortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("an admin may NOT rewrite a budget they do not participate in")
    void adminMayNotWriteToAnotherHouseholdsBudget() {
        authenticateAsAdmin();
        when(budgetRepository.findByIdForParticipant(BUDGET_ID, OUTSIDER)).thenReturn(Optional.empty());

        BudgetItemForm form = new BudgetItemForm();
        form.setLabel("Not mine");
        form.setAmount(new BigDecimal("10.00"));
        form.setFrequency(BudgetFrequency.MONTHLY);
        form.setPaidBySide(BudgetSide.A);

        assertThatThrownBy(() -> service.addItem(BUDGET_ID, form, OUTSIDER))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(BUDGET_ID, OUTSIDER))
                .isInstanceOf(AccessDeniedException.class);

        // The read bypass must not double as a write bypass.
        verify(budgetRepository, never()).findByIdWithParticipants(anyLong());
        verify(budgetItemRepository, never()).save(org.mockito.ArgumentMatchers.any(BudgetItem.class));
    }

    @Test
    @DisplayName("an admin may load a budget they do not participate in")
    void adminMayLoadAnyBudget() {
        authenticateAsAdmin();
        when(budgetRepository.findByIdWithParticipants(BUDGET_ID)).thenReturn(Optional.of(budget()));

        assertThat(service.requireAccess(BUDGET_ID, OUTSIDER).getId()).isEqualTo(BUDGET_ID);
        verify(budgetRepository, never()).findByIdForParticipant(anyLong(), anyLong());
    }

    private void authenticateAsAdmin() {
        User admin = User.builder().id(OUTSIDER).email("admin@x.y").name("Admin")
                .role(com.splitfriend.model.enums.Role.ADMIN).enabled(true).build();
        var principal = new com.splitfriend.security.CustomUserDetailsService.CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private Budget budget() {
        User audrey = User.builder().id(AUDREY).name("Audrey").build();
        User stephane = User.builder().id(STEPHANE).name("Stephane").build();
        return Budget.builder()
                .id(BUDGET_ID)
                .name("Depenses communes")
                .userA(audrey)
                .userB(stephane)
                .shareAPercent(new BigDecimal("50.00"))
                .settlementPeriod(BudgetFrequency.BIWEEKLY)
                .items(new ArrayList<>())
                .build();
    }
}
