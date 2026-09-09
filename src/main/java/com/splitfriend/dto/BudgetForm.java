package com.splitfriend.dto;

import com.splitfriend.model.enums.BudgetFrequency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Create/edit form for a budget. The second participant is picked by id. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetForm {

    @NotBlank
    @Size(min = 1, max = 255)
    private String name;

    private String description;

    /** ISO 4217 code. Constrained here so a tampered POST is a bind error, not a DB failure. */
    @NotNull
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a three-letter code")
    private String currency = "CAD";

    /** The other participant. The creator is always the first one. */
    @NotNull
    private Long partnerId;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal shareAPercent = new BigDecimal("50.00");

    @NotNull
    private BudgetFrequency settlementPeriod = BudgetFrequency.BIWEEKLY;
}
