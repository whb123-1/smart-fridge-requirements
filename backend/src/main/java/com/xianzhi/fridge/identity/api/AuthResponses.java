package com.xianzhi.fridge.identity.api;

import com.xianzhi.fridge.identity.domain.TemperatureUnit;
import java.time.Instant;
import java.util.UUID;

public final class AuthResponses {
    private AuthResponses() { }

    public record User(UUID id, String username, String email, String displayName, String timezone,
                       TemperatureUnit temperatureUnit) { }
    public record Session(String accessToken, Instant accessTokenExpiresAt, User user, boolean onboardingRequired) { }
}
