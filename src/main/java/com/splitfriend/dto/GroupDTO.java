package com.splitfriend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupDTO {

    private Long id;

    @NotBlank(message = "Group name is required")
    @Size(min = 1, max = 255, message = "Group name must be between 1 and 255 characters")
    private String name;

    private String description;

    @Size(max = 3, message = "Currency code must be 3 characters or less")
    private String currency;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupViewDTO {
        private Long id;
        private String name;
        private String description;
        private String currency;
        private LocalDateTime createdAt;
        private String createdByName;
        private int memberCount;
        private BigDecimal totalExpenses;
        private BigDecimal userBalance;
        private List<MemberDTO> members;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberDTO {
        private Long userId;
        private String name;
        private String email;
        private LocalDateTime joinedAt;
        private BigDecimal balance;
    }
}
