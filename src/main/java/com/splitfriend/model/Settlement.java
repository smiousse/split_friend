package com.splitfriend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user", nullable = false)
    private User toUser;

    @NotNull
    @Positive
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "settled_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime settledAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (settledAt == null) {
            settledAt = LocalDateTime.now();
        }
    }
}
