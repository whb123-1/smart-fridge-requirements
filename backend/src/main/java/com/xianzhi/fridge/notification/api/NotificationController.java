package com.xianzhi.fridge.notification.api;

import com.xianzhi.fridge.notification.application.NotificationService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }
    @GetMapping
    public ApiEnvelope<List<NotificationContracts.View>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                               @RequestParam(required = false) Boolean unreadOnly) {
        return ApiEnvelope.ok(notifications.list(principal.userId(), unreadOnly));
    }
    @PatchMapping("/{id}")
    public ApiEnvelope<NotificationContracts.View> update(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody NotificationContracts.UpdateRequest request) {
        return ApiEnvelope.ok(notifications.update(principal.userId(), id, key, request));
    }
}
