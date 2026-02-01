package com.splitfriend.service;

import com.splitfriend.model.Expense;
import com.splitfriend.model.PushSubscription;
import com.splitfriend.model.User;
import com.splitfriend.repository.PushSubscriptionRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSubscriptionRepository subscriptionRepository;

    @Value("${app.push.enabled:false}")
    private boolean pushEnabled;

    @Value("${app.push.vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${app.push.vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${app.push.vapid.subject:mailto:admin@splitfriend.local}")
    private String vapidSubject;

    private PushService pushService;

    public PushNotificationService(PushSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        if (pushEnabled && !vapidPublicKey.isEmpty() && !vapidPrivateKey.isEmpty()) {
            try {
                pushService = new PushService();
                pushService.setPublicKey(vapidPublicKey);
                pushService.setPrivateKey(vapidPrivateKey);
                pushService.setSubject(vapidSubject);
                log.info("Push notification service initialized successfully");
            } catch (GeneralSecurityException e) {
                log.error("Failed to initialize push service: {}", e.getMessage());
                pushEnabled = false;
            } catch (Exception e) {
                log.error("Failed to initialize push service (invalid VAPID key format?): {}", e.getMessage());
                log.info("Push notifications disabled. Generate valid VAPID keys with: mvn exec:java -Dexec.mainClass=\"com.splitfriend.util.VapidKeyGenerator\"");
                pushEnabled = false;
            }
        } else {
            log.info("Push notifications are disabled or VAPID keys not configured");
        }
    }

    public boolean isPushEnabled() {
        return pushEnabled && pushService != null;
    }

    public String getVapidPublicKey() {
        return vapidPublicKey;
    }

    public PushSubscription saveSubscription(User user, String endpoint, String p256dhKey,
                                              String authKey, String userAgent) {
        // Check if subscription already exists
        return subscriptionRepository.findByUserAndEndpoint(user, endpoint)
            .map(existing -> {
                existing.setP256dhKey(p256dhKey);
                existing.setAuthKey(authKey);
                existing.setUserAgent(userAgent);
                existing.setLastUsedAt(LocalDateTime.now());
                return subscriptionRepository.save(existing);
            })
            .orElseGet(() -> {
                PushSubscription subscription = PushSubscription.builder()
                    .user(user)
                    .endpoint(endpoint)
                    .p256dhKey(p256dhKey)
                    .authKey(authKey)
                    .userAgent(userAgent)
                    .build();
                return subscriptionRepository.save(subscription);
            });
    }

    public void deleteSubscription(String endpoint) {
        subscriptionRepository.deleteByEndpoint(endpoint);
    }

    public void deleteSubscription(User user, String endpoint) {
        subscriptionRepository.findByUserAndEndpoint(user, endpoint)
            .ifPresent(subscriptionRepository::delete);
    }

    public boolean isUserSubscribed(User user) {
        return !subscriptionRepository.findByUser(user).isEmpty();
    }

    public List<PushSubscription> getUserSubscriptions(User user) {
        return subscriptionRepository.findByUser(user);
    }

    @Async
    public void sendNotificationToUsers(List<Long> userIds, String title, String body, String url) {
        if (!isPushEnabled()) {
            log.debug("Push notifications disabled, skipping");
            return;
        }

        List<PushSubscription> subscriptions = subscriptionRepository.findByUserIdIn(userIds);
        log.info("Sending push notification to {} subscriptions for {} users",
                 subscriptions.size(), userIds.size());

        for (PushSubscription sub : subscriptions) {
            sendNotification(sub, title, body, url);
        }
    }

    @Async
    public void notifyExpenseParticipants(Expense expense, User payer, List<User> participants) {
        if (!isPushEnabled()) {
            return;
        }

        // Filter out payer from notification recipients
        List<Long> recipientIds = participants.stream()
            .filter(p -> !p.getId().equals(payer.getId()))
            .map(User::getId)
            .collect(Collectors.toList());

        if (recipientIds.isEmpty()) {
            return;
        }

        String title = "New Expense";
        String body = String.format("%s added '%s' ($%.2f) in %s",
            payer.getName(),
            expense.getDescription(),
            expense.getAmount(),
            expense.getGroup().getName());
        String url = "/groups/" + expense.getGroup().getId();

        sendNotificationToUsers(recipientIds, title, body, url);
    }

    private void sendNotification(PushSubscription subscription, String title, String body, String url) {
        try {
            Subscription webPushSubscription = new Subscription(
                subscription.getEndpoint(),
                new Subscription.Keys(subscription.getP256dhKey(), subscription.getAuthKey())
            );

            String payload = String.format(
                "{\"title\":\"%s\",\"body\":\"%s\",\"url\":\"%s\"}",
                escapeJson(title),
                escapeJson(body),
                escapeJson(url)
            );

            Notification notification = new Notification(webPushSubscription, payload);
            pushService.send(notification);

            // Update last used timestamp
            subscription.setLastUsedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);

            log.debug("Push notification sent successfully to endpoint: {}",
                     subscription.getEndpoint().substring(0, Math.min(50, subscription.getEndpoint().length())));

        } catch (Exception e) {
            log.error("Failed to send push notification: {}", e.getMessage());

            // Handle expired/invalid subscriptions (HTTP 410 Gone)
            if (e.getMessage() != null && e.getMessage().contains("410")) {
                log.info("Removing expired subscription: {}", subscription.getId());
                subscriptionRepository.delete(subscription);
            }
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
