package com.xianzhi.fridge.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.shared.config.AppProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AppUserRepository users;
    private final AppProperties properties;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository users, AppProperties properties) {
        this.jwtService = jwtService; this.users=users; this.properties=properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserPrincipal principal = jwtService.parse(header.substring(7));
                String role=users.findById(principal.userId()).map(user -> properties.getSecurity().getAdminUsernames().stream()
                        .anyMatch(name -> name.equalsIgnoreCase(user.getUsername())) ? "ADMIN" : user.getRole()).orElse("USER");
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_"+role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
