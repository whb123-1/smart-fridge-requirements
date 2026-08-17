package com.xianzhi.fridge.identity.application;

import com.xianzhi.fridge.identity.api.AuthRequests;
import com.xianzhi.fridge.identity.api.AuthResponses;
import com.xianzhi.fridge.identity.domain.TemperatureUnit;
import com.xianzhi.fridge.identity.infrastructure.AppUser;
import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.identity.infrastructure.RefreshSession;
import com.xianzhi.fridge.identity.infrastructure.RefreshSessionRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.application.RateLimiter;
import com.xianzhi.fridge.shared.config.AppProperties;
import com.xianzhi.fridge.shared.domain.Hashing;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.security.JwtService;
import com.xianzhi.fridge.shared.web.ApiException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AppUserRepository users;
    private final RefreshSessionRepository refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    public AuthService(AppUserRepository users, RefreshSessionRepository refreshSessions, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AppProperties properties, RateLimiter rateLimiter, AuditService audit) {
        this.users = users;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @Transactional
    public SessionIssue register(AuthRequests.Register request, ClientContext client) {
        String email = normalizeEmail(request.email());
        String username = normalizeUsername(request.username());
        if (users.findByEmailAndDeletedAtIsNull(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account already uses this email");
        }
        if (users.findByUsername(username).isPresent()) throw usernameConflict();
        AppUser user = new AppUser(UuidV7.next(), username, email, passwordEncoder.encode(request.password()),
                request.displayName().trim(), properties.getTimezone());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException conflict) {
            throw registrationConflict(conflict);
        }
        audit.record(user.getId(), "AUTH_REGISTERED");
        return issue(user, client, UuidV7.next());
    }

    @Transactional
    public SessionIssue login(AuthRequests.Login request, ClientContext client) {
        String identifier = normalizeIdentifier(request.identifier());
        String ipKey = "rate:login:ip:" + Hashing.sha256(client.ipAddress());
        if (rateLimiter.exceeded(ipKey, properties.getRateLimit().getLoginPerIpPerMinute(), Duration.ofMinutes(1))) {
            throw ApiException.rateLimited(Duration.ofMinutes(1));
        }
        if (rateLimiter.exceeded("rate:login:account:" + Hashing.sha256(identifier),
                properties.getRateLimit().getLoginPerAccountPer15Minutes(), Duration.ofMinutes(15))) {
            throw ApiException.rateLimited(Duration.ofMinutes(15));
        }
        AppUser user = identifier.contains("@")
                ? users.findByEmailAndDeletedAtIsNull(identifier).orElse(null)
                : users.findByUsernameAndDeletedAtIsNull(identifier).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            audit.record(user == null ? null : user.getId(), "AUTH_LOGIN_FAILED");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Email/username or password is incorrect");
        }
        audit.record(user.getId(), "AUTH_LOGIN_SUCCEEDED");
        return issue(user, client, UuidV7.next());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SessionIssue refresh(String rawToken, ClientContext client) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Refresh session is missing");
        }
        RefreshSession previous = refreshSessions.findByTokenHash(Hashing.sha256(rawToken)).orElse(null);
        if (previous == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Refresh session is invalid");
        Instant now = clock.instant();
        if (!previous.isUsableAt(now)) {
            revokeFamily(previous.getFamilyId(), null);
            audit.record(previous.getUserId(), "AUTH_REFRESH_REPLAY_DETECTED");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Refresh session is no longer valid");
        }
        AppUser user = users.lockActiveById(previous.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "User is unavailable"));
        SessionIssue next = issue(user, client, previous.getFamilyId());
        previous.revoke(now, next.sessionId());
        audit.record(user.getId(), "AUTH_REFRESHED");
        return next;
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshSessions.findByTokenHash(Hashing.sha256(rawToken)).ifPresent(session -> {
            revokeFamily(session.getFamilyId(), null);
            audit.record(session.getUserId(), "AUTH_LOGOUT");
        });
    }

    @Transactional(readOnly = true)
    public AuthResponses.User me(UUID userId) {
        return toUser(users.findById(userId).filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "User is unavailable")));
    }

    @Transactional
    public AuthResponses.User updateProfile(UUID userId, AuthRequests.UpdateProfile request) {
        AppUser user = users.lockActiveById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "User is unavailable"));
        String username = normalizeUsername(request.username());
        if (users.findByUsername(username).filter(existing -> !existing.getId().equals(userId)).isPresent()) {
            throw usernameConflict();
        }
        try {
            java.time.ZoneId.of(request.timezone());
        } catch (RuntimeException invalidZone) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Timezone is invalid");
        }
        user.updateProfile(username, request.displayName().trim(), request.timezone(),
                request.temperatureUnit() == null ? TemperatureUnit.C : request.temperatureUnit());
        try {
            users.flush();
        } catch (DataIntegrityViolationException conflict) {
            throw usernameConflict();
        }
        audit.record(userId, "USER_PROFILE_UPDATED");
        return toUser(user);
    }

    @Transactional
    public void changePassword(UUID userId, AuthRequests.ChangePassword request) {
        AppUser user = users.lockActiveById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "User is unavailable"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            audit.record(userId, "AUTH_PASSWORD_CHANGE_FAILED");
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        revokeUserSessions(userId);
        audit.record(userId, "AUTH_PASSWORD_CHANGED");
    }

    private SessionIssue issue(AppUser user, ClientContext client, UUID familyId) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID sessionId = UuidV7.next();
        refreshSessions.save(new RefreshSession(sessionId, user.getId(), Hashing.sha256(rawRefreshToken), familyId,
                clock.instant().plus(properties.getSecurity().getRefreshTtl()), Hashing.sha256(client.ipAddress()), client.userAgent()));
        JwtService.AccessToken accessToken = jwtService.issue(user.getId());
        return new SessionIssue(sessionId, rawRefreshToken,
                new AuthResponses.Session(accessToken.value(), accessToken.expiresAt(), toUser(user), user.onboardingRequired()));
    }

    private void revokeFamily(UUID familyId, UUID replacement) {
        Instant now = clock.instant();
        for (RefreshSession active : refreshSessions.findByFamilyIdAndRevokedAtIsNull(familyId)) active.revoke(now, replacement);
    }

    private void revokeUserSessions(UUID userId) {
        Instant now = clock.instant();
        for (RefreshSession active : refreshSessions.findByUserIdAndRevokedAtIsNull(userId)) active.revoke(now, null);
    }

    private static String normalizeEmail(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static String normalizeUsername(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static String normalizeIdentifier(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static AuthResponses.User toUser(AppUser user) {
        return new AuthResponses.User(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
                user.getTimezone(), user.getTemperatureUnit());
    }

    private static ApiException usernameConflict() {
        return new ApiException(HttpStatus.CONFLICT, "USERNAME_ALREADY_REGISTERED",
                "An account already uses this username");
    }

    private static ApiException registrationConflict(DataIntegrityViolationException conflict) {
        String details = conflict.getMostSpecificCause().getMessage();
        if (details != null && details.contains("uk_app_user_username")) return usernameConflict();
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                "An account already uses this email");
    }

    public record ClientContext(String ipAddress, String userAgent) {
        public ClientContext { ipAddress = ipAddress == null ? "unknown" : ipAddress; userAgent = userAgent == null ? "" : userAgent.substring(0, Math.min(512, userAgent.length())); }
    }
    public record SessionIssue(UUID sessionId, String refreshToken, AuthResponses.Session session) { }
}
