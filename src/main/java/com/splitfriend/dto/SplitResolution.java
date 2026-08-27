package com.splitfriend.dto;

import com.splitfriend.model.User;
import com.splitfriend.model.enums.SplitType;

import java.util.List;

/**
 * Immutable result of resolving a quick split mode into the values
 * {@code ExpenseService.createExpense} expects.
 */
public record SplitResolution(SplitType splitType, List<User> participants) {

    public SplitResolution(SplitType splitType, List<User> participants) {
        this.splitType = splitType;
        this.participants = List.copyOf(participants);
    }
}
