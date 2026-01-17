package com.splitfriend.dto;

import com.splitfriend.model.enums.SplitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseDTO {

    private Long id;

    @NotNull(message = "Group is required")
    private Long groupId;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Split type is required")
    private SplitType splitType;

    private LocalDate expenseDate;

    private Long paidById;

    private List<Long> participantIds;

    // For exact amounts
    private Map<Long, BigDecimal> exactAmounts;

    // For percentage splits
    private Map<Long, BigDecimal> percentages;

    // For shares splits
    private Map<Long, Integer> shares;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseViewDTO {
        private Long id;
        private String description;
        private BigDecimal amount;
        private String splitType;
        private LocalDate expenseDate;
        private String paidByName;
        private Long paidById;
        private String groupName;
        private Long groupId;
        private List<SplitViewDTO> splits;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitViewDTO {
        private Long userId;
        private String userName;
        private BigDecimal amount;
        private BigDecimal percentage;
        private Integer shares;
    }
}
