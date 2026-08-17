package com.xianzhi.fridge.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UuidV7Test {
    @Test
    void createsVersionSevenRfc4122Uuid() {
        var value = UuidV7.next();
        assertThat(value.version()).isEqualTo(7);
        assertThat(value.variant()).isEqualTo(2);
    }
}
