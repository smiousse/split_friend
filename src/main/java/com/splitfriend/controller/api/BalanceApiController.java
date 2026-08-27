package com.splitfriend.controller.api;

import com.splitfriend.dto.ApiBalanceResponse;
import com.splitfriend.dto.BalanceDTO;
import com.splitfriend.model.Group;
import com.splitfriend.model.User;
import com.splitfriend.security.CustomUserDetailsService;
import com.splitfriend.service.BalanceService;
import com.splitfriend.service.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only balance API for external clients on the local network, authenticated
 * by the {@code X-API-Token} header (see
 * {@link com.splitfriend.security.ApiTokenAuthFilter}).
 */
@RestController
@RequestMapping("/api/v1")
public class BalanceApiController {

    private static final int SCALE = 2;

    /** Amounts below this are rounding noise, not a real debt. */
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    private final GroupService groupService;
    private final BalanceService balanceService;

    public BalanceApiController(GroupService groupService, BalanceService balanceService) {
        this.groupService = groupService;
        this.balanceService = balanceService;
    }

    @GetMapping("/balances")
    public ResponseEntity<ApiBalanceResponse> balances(
            @AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails) {

        User user = userDetails.getUser();

        // Per counterparty, netted across every group: positive means they owe the caller
        Map<Long, BigDecimal> nets = new LinkedHashMap<>();
        Map<Long, String> names = new HashMap<>();
        collectCounterparties(user, nets, names);

        List<ApiBalanceResponse.Person> iOwe = new ArrayList<>();
        List<ApiBalanceResponse.Person> owedToMe = new ArrayList<>();
        BigDecimal totalIOwe = BigDecimal.ZERO;
        BigDecimal totalOwedToMe = BigDecimal.ZERO;

        for (Map.Entry<Long, BigDecimal> entry : nets.entrySet()) {
            BigDecimal net = entry.getValue();
            BigDecimal amount = net.abs();
            if (amount.compareTo(MIN_AMOUNT) < 0) {
                continue;
            }
            ApiBalanceResponse.Person person =
                    new ApiBalanceResponse.Person(names.get(entry.getKey()), scale(amount));
            if (net.signum() > 0) {
                owedToMe.add(person);
                totalOwedToMe = totalOwedToMe.add(amount);
            } else {
                iOwe.add(person);
                totalIOwe = totalIOwe.add(amount);
            }
        }

        // Largest debts first
        Comparator<ApiBalanceResponse.Person> byAmountDesc =
                Comparator.comparing(ApiBalanceResponse.Person::amount).reversed();
        iOwe.sort(byAmountDesc);
        owedToMe.sort(byAmountDesc);

        return ResponseEntity.ok(new ApiBalanceResponse(
                user.getName(),
                scale(totalIOwe),
                scale(totalOwedToMe),
                scale(totalOwedToMe.subtract(totalIOwe)),
                iOwe,
                owedToMe
        ));
    }

    /**
     * Walks every group the user belongs to and accumulates, per other person, the
     * net amount between them and the caller. Positive means that person owes the
     * caller. Debts between two other members are ignored.
     */
    private void collectCounterparties(User user, Map<Long, BigDecimal> nets, Map<Long, String> names) {
        Long userId = user.getId();

        for (Group group : groupService.findByUser(user)) {
            // calculateDebts already reduces each group to a minimal set of pairwise payments
            for (BalanceDTO.DebtDTO debt : balanceService.calculateDebts(group.getId())) {
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
