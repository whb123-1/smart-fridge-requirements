package com.xianzhi.fridge.shared.security;

import com.xianzhi.fridge.shared.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final AppProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        this.clock = Clock.systemUTC();
        byte[] key = properties.getSecurity().getJwtSigningKey().getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new IllegalStateException("JWT_SIGNING_KEY must be at least 32 bytes");
        this.signingKey = Keys.hmacShaKeyFor(key);
    }

    public AccessToken issue(UUID userId) {
        Instant expiresAt = clock.instant().plus(properties.getSecurity().getAccessTtl());
        String token = Jwts.builder()
                .issuer(properties.getSecurity().getJwtIssuer())
                .subject(userId.toString())
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new AccessToken(token, expiresAt);
    }

    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getSecurity().getJwtIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new UserPrincipal(UUID.fromString(claims.getSubject()));
    }

    public record AccessToken(String value, Instant expiresAt) { }
}
