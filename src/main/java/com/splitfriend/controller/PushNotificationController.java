package com.splitfriend.controller;

import com.splitfriend.model.User;
import com.splitfriend.service.PushNotificationService;
import com.splitfriend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushNotificationController {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationController.class);

    private final PushNotificationService pushNotificationService;
    private final UserService userService;

    public PushNotificationController(PushNotificationService pushNotificationService,
                                       UserService userService) {
        this.pushNotificationService = pushNotificationService;
        this.userService = userService;
    }

    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, Object>> getVapidPublicKey() {
        if (!pushNotificationService.isPushEnabled()) {
            return ResponseEntity.ok(Map.of(
                "enabled", false,
                "publicKey", ""
            ));
        }
        return ResponseEntity.ok(Map.of(
            "enabled", true,
            "publicKey", pushNotificationService.getVapidPublicKey()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isSubscribed = pushNotificationService.isUserSubscribed(user);
        boolean isEnabled = pushNotificationService.isPushEnabled();

        return ResponseEntity.ok(Map.of(
            "pushEnabled", isEnabled,
            "isSubscribed", isSubscribed,
            "subscriptionCount", pushNotificationService.getUserSubscriptions(user).size()
        ));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody SubscriptionRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        if (!pushNotificationService.isPushEnabled()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Push notifications are not enabled"
            ));
        }

        User user = userService.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            pushNotificationService.saveSubscription(
                user,
                request.endpoint(),
                request.keys().p256dh(),
                request.keys().auth(),
                userAgent
            );

            log.info("User {} subscribed to push notifications", user.getEmail());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscribed to push notifications"
            ));
        } catch (Exception e) {
            log.error("Failed to save subscription for user {}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to save subscription"
            ));
        }
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) UnsubscribeRequest request) {

        User user = userService.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            if (request != null && request.endpoint() != null) {
                pushNotificationService.deleteSubscription(user, request.endpoint());
            } else {
                // Delete all subscriptions for the user
                pushNotificationService.getUserSubscriptions(user)
                    .forEach(sub -> pushNotificationService.deleteSubscription(sub.getEndpoint()));
            }

            log.info("User {} unsubscribed from push notifications", user.getEmail());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Unsubscribed from push notifications"
            ));
        } catch (Exception e) {
            log.error("Failed to unsubscribe user {}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to unsubscribe"
            ));
        }
    }

    public record SubscriptionRequest(
        String endpoint,
        SubscriptionKeys keys
    ) {}

    public record SubscriptionKeys(
        String p256dh,
        String auth
    ) {}

    public record UnsubscribeRequest(
        String endpoint
    ) {}
}
