package com.xianzhi.fridge.identity.api;

import com.xianzhi.fridge.identity.application.UserPreferenceService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/me/preferences")
public class UserPreferenceController {
    private final UserPreferenceService service;public UserPreferenceController(UserPreferenceService service){this.service=service;}
    @GetMapping public ApiEnvelope<PreferenceContracts.View> get(@AuthenticationPrincipal UserPrincipal principal){return ApiEnvelope.ok(service.get(principal.userId()));}
    @PutMapping public ApiEnvelope<PreferenceContracts.View> update(@AuthenticationPrincipal UserPrincipal principal,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PreferenceContracts.UpdateRequest request){return ApiEnvelope.ok(service.update(principal.userId(),key,request));}
}
