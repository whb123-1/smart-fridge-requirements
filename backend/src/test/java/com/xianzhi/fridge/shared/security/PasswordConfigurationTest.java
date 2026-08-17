package com.xianzhi.fridge.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigurationTest {
    @Test
    void usesTheRequiredArgon2idCostParameters() {
        PasswordEncoder encoder = new SecurityConfiguration().passwordEncoder();

        String encoded = encoder.encode("correct horse battery staple");

        assertThat(encoded).startsWith("$argon2id$v=19$m=65536,t=3,p=1$");
        assertThat(encoder.matches("correct horse battery staple", encoded)).isTrue();
        assertThat(encoder.matches("wrong password", encoded)).isFalse();
    }
}
