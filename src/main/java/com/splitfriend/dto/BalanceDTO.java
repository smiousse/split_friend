package com.splitfriend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceDTO {
    private Long userId;
    private String userName;
    private String userEmail;
    private BigDecimal balance;

    public boolean isPositive() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isZero() {
        return balance == null || balance.compareTo(BigDecimal.ZERO) == 0;
    }

    public BigDecimal getAbsoluteBalance() {
        return balance != null ? balance.abs() : BigDecimal.ZERO;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebtDTO {
        private Long fromUserId;
        private String fromUserName;
        private Long toUserId;
        private String toUserName;
        private BigDecimal amount;
    }
}
