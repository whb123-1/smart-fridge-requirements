package com.xianzhi.fridge.notification.api;

import com.xianzhi.fridge.notification.application.NotificationPreferenceService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/me/notification-preferences")
public class NotificationPreferenceController {
    private final NotificationPreferenceService service;public NotificationPreferenceController(NotificationPreferenceService service){this.service=service;}
    @GetMapping public ApiEnvelope<List<NotificationPreferenceContracts.View>> get(@AuthenticationPrincipal UserPrincipal principal){return ApiEnvelope.ok(service.get(principal.userId()));}
    @PutMapping public ApiEnvelope<List<NotificationPreferenceContracts.View>> update(@AuthenticationPrincipal UserPrincipal principal,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody NotificationPreferenceContracts.UpdateRequest request){return ApiEnvelope.ok(service.update(principal.userId(),key,request));}
}
