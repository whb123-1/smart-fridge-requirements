package com.xianzhi.fridge.fridge.api;

import com.xianzhi.fridge.fridge.application.OnboardingService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fridges")
public class FridgeController {
    private final OnboardingService onboarding;
    public FridgeController(OnboardingService onboarding) { this.onboarding = onboarding; }
    @GetMapping
    public ApiEnvelope<List<OnboardingContracts.FridgeSummary>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.ok(onboarding.listFridges(principal.userId()));
    }
}
