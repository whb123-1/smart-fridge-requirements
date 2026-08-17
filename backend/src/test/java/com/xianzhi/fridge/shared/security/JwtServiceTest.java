package com.xianzhi.fridge.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.xianzhi.fridge.shared.config.AppProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    @Test
    void issuesAndParsesAccessToken() {
        AppProperties properties = new AppProperties();
        properties.getSecurity().setJwtSigningKey("test-signing-key-that-is-longer-than-32-bytes");
        properties.getSecurity().setAccessTtl(Duration.ofMinutes(15));
        JwtService service = new JwtService(properties);
        UUID userId = UUID.randomUUID();

        JwtService.AccessToken token = service.issue(userId);

        assertThat(service.parse(token.value()).userId()).isEqualTo(userId);
    }
}
