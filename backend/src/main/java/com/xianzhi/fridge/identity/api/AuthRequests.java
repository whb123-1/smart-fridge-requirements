package com.xianzhi.fridge.identity.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.xianzhi.fridge.identity.domain.TemperatureUnit;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() { }

    public record Register(
            @NotBlank @Pattern(regexp = "^[a-z0-9_]{3,32}$") String username,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 80) String displayName) { }

    public record Login(
            @JsonAlias("email") @NotBlank @Size(max = 320) String identifier,
            @NotBlank @Size(min = 6, max = 128) String password) { }

    public record UpdateProfile(
            @NotBlank @Pattern(regexp = "^[a-z0-9_]{3,32}$") String username,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Pattern(regexp = "[A-Za-z_]+/[A-Za-z_]+") @Size(max = 64) String timezone,
            TemperatureUnit temperatureUnit) { }

    public record ChangePassword(
            @NotBlank @Size(min = 8, max = 128) String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) { }
}
