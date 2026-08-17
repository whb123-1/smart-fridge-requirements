package com.xianzhi.fridge.fridge.domain;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

public record ZoneDefaults(String suggestedName, BigDecimal targetTemperatureC, BigDecimal targetHumidityPct,
                           BigDecimal safeTemperatureMinC, BigDecimal safeTemperatureMaxC,
                           BigDecimal safeHumidityMinPct, BigDecimal safeHumidityMaxPct) {
    private static final Map<ZoneKind, ZoneDefaults> VALUES = defaults();

    public static ZoneDefaults forKind(ZoneKind kind) { return VALUES.get(kind); }

    private static Map<ZoneKind, ZoneDefaults> defaults() {
        Map<ZoneKind, ZoneDefaults> values = new EnumMap<>(ZoneKind.class);
        values.put(ZoneKind.CHILL, new ZoneDefaults("冷藏区", decimal("4"), decimal("70"), decimal("1"), decimal("6"), decimal("60"), decimal("80")));
        values.put(ZoneKind.FRESH, new ZoneDefaults("保鲜区", decimal("2"), decimal("85"), decimal("0"), decimal("4"), decimal("75"), decimal("95")));
        values.put(ZoneKind.VARIABLE, new ZoneDefaults("变温区", decimal("4"), decimal("65"), decimal("1"), decimal("7"), decimal("50"), decimal("75")));
        values.put(ZoneKind.FREEZE, new ZoneDefaults("冷冻区", decimal("-20"), decimal("45"), decimal("-24"), decimal("-16"), decimal("30"), decimal("55")));
        return Map.copyOf(values);
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
}
