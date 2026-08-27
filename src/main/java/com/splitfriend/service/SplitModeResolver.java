package com.splitfriend.service;

import com.splitfriend.dto.SplitResolution;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.SplitMode;
import com.splitfriend.model.enums.SplitType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Turns the add-expense form's quick sharing choice into a concrete
 * {@link SplitType} and participant list. The client sends only the mode;
 * the participant set is always derived here so a tampered form cannot
 * create splits the mode does not allow.
 */
@Service
public class SplitModeResolver {

    public SplitResolution resolve(SplitMode mode,
                                   SplitType splitType,
                                   List<User> allMembers,
                                   List<User> selectedMembers,
                                   Long payerId,
                                   Long currentUserId) {

        SplitMode effectiveMode = mode != null ? mode : SplitMode.SPLIT;

        return switch (effectiveMode) {
            case SPLIT -> new SplitResolution(
                    splitType != null ? splitType : SplitType.EQUAL,
                    requireParticipants(selectedMembers, "No participant selected"));
            case I_OWE_ALL -> new SplitResolution(SplitType.EQUAL, currentUserOnly(allMembers, currentUserId));
            case THEY_OWE_ALL -> new SplitResolution(SplitType.EQUAL, othersThanPayer(allMembers, selectedMembers, payerId));
        };
    }

    private List<User> currentUserOnly(List<User> allMembers, Long currentUserId) {
        return requireParticipants(
                allMembers.stream()
                        .filter(u -> Objects.equals(u.getId(), currentUserId))
                        .collect(Collectors.toList()),
                "You are not a member of this group");
    }

    private List<User> othersThanPayer(List<User> allMembers, List<User> selectedMembers, Long payerId) {
        List<User> selectedOthers = withoutPayer(selectedMembers, payerId);
        if (!selectedOthers.isEmpty()) {
            return selectedOthers;
        }
        return requireParticipants(withoutPayer(allMembers, payerId),
                "The payer is the only group member, nobody else can owe the amount");
    }

    private List<User> withoutPayer(List<User> members, Long payerId) {
        return members.stream()
                .filter(u -> !Objects.equals(u.getId(), payerId))
                .collect(Collectors.toList());
    }

    private List<User> requireParticipants(List<User> participants, String message) {
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return participants;
    }
}
