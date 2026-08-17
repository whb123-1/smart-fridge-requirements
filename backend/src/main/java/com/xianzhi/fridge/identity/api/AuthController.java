package com.xianzhi.fridge.identity.api;

import com.xianzhi.fridge.identity.application.AuthService;
import com.xianzhi.fridge.shared.config.AppProperties;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final AppProperties properties;

    public AuthController(AuthService auth, AppProperties properties) { this.auth = auth; this.properties = properties; }

    @PostMapping("/register")
    public ResponseEntity<ApiEnvelope<AuthResponses.Session>> register(@Valid @RequestBody AuthRequests.Register request,
                                                                        HttpServletRequest http, HttpServletResponse response) {
        AuthService.SessionIssue issue = auth.register(request, client(http));
        writeRefreshCookie(response, issue.refreshToken(), properties.getSecurity().getRefreshTtl());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.ok(issue.session()));
    }

    @PostMapping("/login")
    public ApiEnvelope<AuthResponses.Session> login(@Valid @RequestBody AuthRequests.Login request,
                                                    HttpServletRequest http, HttpServletResponse response) {
        AuthService.SessionIssue issue = auth.login(request, client(http));
        writeRefreshCookie(response, issue.refreshToken(), properties.getSecurity().getRefreshTtl());
        return ApiEnvelope.ok(issue.session());
    }

    @PostMapping("/refresh")
    public ApiEnvelope<AuthResponses.Session> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthService.SessionIssue issue = auth.refresh(readCookie(request), client(request));
        writeRefreshCookie(response, issue.refreshToken(), properties.getSecurity().getRefreshTtl());
        return ApiEnvelope.ok(issue.session());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiEnvelope<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(readCookie(request));
        writeRefreshCookie(response, "", Duration.ZERO);
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    private AuthService.ClientContext client(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return new AuthService.ClientContext(ip, request.getHeader("User-Agent"));
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (properties.getSecurity().getRefreshCookieName().equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private void writeRefreshCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(properties.getSecurity().getRefreshCookieName(), value)
                .httpOnly(true).secure(properties.getSecurity().isRefreshCookieSecure()).sameSite("Lax")
                .path("/api/v1/auth").maxAge(maxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
