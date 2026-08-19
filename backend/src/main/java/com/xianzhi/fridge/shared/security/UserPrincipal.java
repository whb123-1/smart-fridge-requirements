package com.xianzhi.fridge.shared.security;

import java.util.UUID;

public record UserPrincipal(UUID userId, long sessionVersion) {
    public UserPrincipal(UUID userId) { this(userId, 0); }
}
