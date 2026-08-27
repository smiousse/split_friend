package com.splitfriend.service;

import com.splitfriend.dto.BalanceDTO;
import com.splitfriend.dto.PersonalBalance;
import com.splitfriend.model.Group;
import com.splitfriend.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the group-level debt lists produced by {@link BalanceService} into one
 * user's per-person view: who they owe, and who owes them.
 *
 * Single source of truth for that question - used by the dashboard panel, the
 * group view panel, and the read-only balance API.
 */
@Service
@Transactional(readOnly = true)
public class PersonalBalanceService {

    private static final int SCALE = 2;

    /** Amounts below this are rounding noise, not a real debt. */
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    private final GroupService groupService;
    private final BalanceService balanceService;

    public PersonalBalanceService(GroupService groupService, BalanceService balanceService) {
        this.groupService = groupService;
        this.balanceService = balanceService;
    }

    /**
     * The user's position across every group they belong to. Amounts are netted
     * per person, so someone the user both owes and is owed appears once, for
     * the difference only.
     */
    public PersonalBalance summarize(User user) {
        List<Long> groupIds = new ArrayList<>();
        for (Group group : groupService.findByUser(user)) {
            groupIds.add(group.getId());
        }
        return summarize(user.getId(), groupIds);
    }

    /**
     * The user's position within a single group. Within one group
     * {@link BalanceService#calculateDebts} already emits at most one debt per
     * pair, so there is nothing to net - only the user's own rows are kept.
     */
    public PersonalBalance summarizeInGroup(User user, Long groupId) {
        return summarize(user.getId(), List.of(groupId));
    }

    private PersonalBalance summarize(Long userId, List<Long> groupIds) {
        // Per counterparty: positive means they owe the user, negative means the user owes them
        Map<Long, BigDecimal> nets = new LinkedHashMap<>();
        Map<Long, String> names = new HashMap<>();
        collectCounterparties(userId, groupIds, nets, names);

        List<PersonalBalance.PersonShare> iOwe = new ArrayList<>();
        List<PersonalBalance.PersonShare> owedToMe = new ArrayList<>();
        BigDecimal totalIOwe = BigDecimal.ZERO;
        BigDecimal totalOwedToMe = BigDecimal.ZERO;

        for (Map.Entry<Long, BigDecimal> entry : nets.entrySet()) {
            BigDecimal net = entry.getValue();
            BigDecimal amount = net.abs();
            if (amount.compareTo(MIN_AMOUNT) < 0) {
                continue;
            }
            PersonalBalance.PersonShare share = new PersonalBalance.PersonShare(
                    entry.getKey(), names.get(entry.getKey()), scale(amount));
            if (net.signum() > 0) {
                owedToMe.add(share);
                totalOwedToMe = totalOwedToMe.add(amount);
            } else {
                iOwe.add(share);
                totalIOwe = totalIOwe.add(amount);
            }
        }

        // Largest debts first
        Comparator<PersonalBalance.PersonShare> byAmountDesc =
                Comparator.comparing(PersonalBalance.PersonShare::amount).reversed();
        iOwe.sort(byAmountDesc);
        owedToMe.sort(byAmountDesc);

        return new PersonalBalance(
                scale(totalIOwe),
                scale(totalOwedToMe),
                scale(totalOwedToMe.subtract(totalIOwe)),
                iOwe,
                owedToMe
        );
    }

    /**
     * Accumulates, per other person, the net amount between them and the user.
     * Debts between two other members are ignored.
     */
    private void collectCounterparties(Long userId,
                                      List<Long> groupIds,
                                      Map<Long, BigDecimal> nets,
                                      Map<Long, String> names) {
        for (Long groupId : groupIds) {
            // calculateDebts already reduces each group to a minimal set of pairwise payments
            for (BalanceDTO.DebtDTO debt : balanceService.calculateDebts(groupId)) {
                if (userId.equals(debt.getFromUserId())) {
                    nets.merge(debt.getToUserId(), debt.getAmount().negate(), BigDecimal::add);
                    names.putIfAbsent(debt.getToUserId(), debt.getToUserName());
                } else if (userId.equals(debt.getToUserId())) {
                    nets.merge(debt.getFromUserId(), debt.getAmount(), BigDecimal::add);
                    names.putIfAbsent(debt.getFromUserId(), debt.getFromUserName());
                }
            }
        }
    }

    private static BigDecimal scale(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
