package com.splitfriend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementDTO {

    private Long id;

    @NotNull(message = "Group is required")
    private Long groupId;

    @NotNull(message = "Payer is required")
    private Long fromUserId;

    @NotNull(message = "Recipient is required")
    private Long toUserId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private LocalDateTime settledAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementViewDTO {
        private Long id;
        private Long groupId;
        private String groupName;
        private Long fromUserId;
        private String fromUserName;
        private Long toUserId;
        private String toUserName;
        private BigDecimal amount;
        private LocalDateTime settledAt;
    }
}
