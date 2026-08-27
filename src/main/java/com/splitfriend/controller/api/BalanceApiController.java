package com.splitfriend.controller.api;

import com.splitfriend.dto.ApiBalanceResponse;
import com.splitfriend.dto.PersonalBalance;
import com.splitfriend.model.User;
import com.splitfriend.security.CustomUserDetailsService;
import com.splitfriend.service.PersonalBalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only balance API for external clients on the local network, authenticated
 * by the {@code X-API-Token} header (see
 * {@link com.splitfriend.security.ApiTokenAuthFilter}).
 *
 * All of the arithmetic lives in {@link PersonalBalanceService}, shared with the
 * dashboard and group-view panels; this controller only maps its result onto the
 * published JSON shape.
 */
@RestController
@RequestMapping("/api/v1")
public class BalanceApiController {

    private final PersonalBalanceService personalBalanceService;

    public BalanceApiController(PersonalBalanceService personalBalanceService) {
        this.personalBalanceService = personalBalanceService;
    }

    @GetMapping("/balances")
    public ResponseEntity<ApiBalanceResponse> balances(
            @AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails) {

        User user = userDetails.getUser();
        PersonalBalance summary = personalBalanceService.summarize(user);

        return ResponseEntity.ok(new ApiBalanceResponse(
                user.getName(),
                summary.totalIOwe(),
                summary.totalOwedToMe(),
                summary.net(),
                toPeople(summary.iOwe()),
                toPeople(summary.owedToMe())
        ));
    }

    /** Drops the user id, which the API deliberately does not expose. */
    private static List<ApiBalanceResponse.Person> toPeople(List<PersonalBalance.PersonShare> shares) {
        return shares.stream()
                .map(share -> new ApiBalanceResponse.Person(share.name(), share.amount()))
                .toList();
    }
}
