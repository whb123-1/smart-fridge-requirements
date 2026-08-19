package com.xianzhi.fridge.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {
    private final SecurityErrorWriter errors;
    public PasswordChangeRequiredFilter(SecurityErrorWriter errors) { this.errors = errors; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(value -> "PASSWORD_CHANGE_REQUIRED".equals(value.getAuthority())) && !allowed(request)) {
            errors.write(response, HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                    "Password must be changed before using the application");
            return;
        }
        chain.doFilter(request, response);
    }
    private boolean allowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/me") || path.equals("/api/v1/me/password") ||
                path.equals("/api/v1/auth/logout") ||
                path.startsWith("/actuator/health");
    }
}
