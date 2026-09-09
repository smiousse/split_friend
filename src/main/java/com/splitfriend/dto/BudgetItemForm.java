package com.splitfriend.dto;

import com.splitfriend.model.enums.BudgetFrequency;
import com.splitfriend.model.enums.BudgetItemType;
import com.splitfriend.model.enums.BudgetSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Create/edit form for one budget line.
 *
 * The payer is posted as a {@link BudgetSide}, not a user id, so a tampered
 * form cannot attribute a line to somebody outside the budget.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetItemForm {

    private Long id;

    @NotBlank
    private String label;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private BudgetFrequency frequency = BudgetFrequency.MONTHLY;

    @NotNull
    private BudgetSide paidBySide = BudgetSide.A;

    @NotNull
    private BudgetItemType itemType = BudgetItemType.SHARED;

    private boolean active = true;

    private String notes;
}
