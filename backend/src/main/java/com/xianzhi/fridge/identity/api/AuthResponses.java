package com.xianzhi.fridge.identity.api;

import com.xianzhi.fridge.identity.domain.TemperatureUnit;
import com.xianzhi.fridge.identity.domain.UserRole;
import com.xianzhi.fridge.identity.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public final class AuthResponses {
    private AuthResponses() { }

    public record User(UUID id, String username, String email, String displayName, String timezone,
                       TemperatureUnit temperatureUnit, UserRole role, UserStatus status,
                       boolean passwordChangeRequired) { }
    public record Session(String accessToken, Instant accessTokenExpiresAt, User user, boolean onboardingRequired) { }
}
