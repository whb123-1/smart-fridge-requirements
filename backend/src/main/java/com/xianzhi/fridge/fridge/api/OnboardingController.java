package com.xianzhi.fridge.fridge.api;

import com.xianzhi.fridge.fridge.application.OnboardingService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {
    private final OnboardingService onboarding;
    public OnboardingController(OnboardingService onboarding) { this.onboarding = onboarding; }

    @GetMapping
    public ApiEnvelope<OnboardingContracts.Status> status(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.ok(onboarding.status(principal.userId()));
    }

    @PostMapping("/initialize")
    public ApiEnvelope<OnboardingContracts.FridgeSummary> initialize(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OnboardingContracts.InitializeRequest request) {
        return ApiEnvelope.ok(onboarding.initialize(principal.userId(), idempotencyKey, request));
    }
}
