package com.xianzhi.fridge.fridge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZoneDefaultsTest {
    @Test
    void freezerTargetStaysInsideSafeRange() {
        ZoneDefaults defaults = ZoneDefaults.forKind(ZoneKind.FREEZE);
        assertThat(defaults.targetTemperatureC()).isBetween(
                defaults.safeTemperatureMinC(), defaults.safeTemperatureMaxC());
    }

    @Test
    void everySupportedZoneHasDefaults() {
        for (ZoneKind kind : ZoneKind.values()) assertThat(ZoneDefaults.forKind(kind)).isNotNull();
    }
}
