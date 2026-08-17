package com.xianzhi.fridge.identity.api;

import com.xianzhi.fridge.identity.application.AuthService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    private final AuthService auth;
    public MeController(AuthService auth) { this.auth = auth; }

    @GetMapping
    public ApiEnvelope<AuthResponses.User> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.ok(auth.me(principal.userId()));
    }

    @PatchMapping
    public ApiEnvelope<AuthResponses.User> update(@AuthenticationPrincipal UserPrincipal principal,
                                                   @Valid @RequestBody AuthRequests.UpdateProfile request) {
        return ApiEnvelope.ok(auth.updateProfile(principal.userId(), request));
    }

    @PatchMapping("/password")
    public ResponseEntity<ApiEnvelope<Void>> password(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Valid @RequestBody AuthRequests.ChangePassword request) {
        auth.changePassword(principal.userId(), request);
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }
}
