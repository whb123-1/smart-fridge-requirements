package com.xianzhi.fridge.fridge.api;

import com.xianzhi.fridge.fridge.domain.DeviceType;
import com.xianzhi.fridge.fridge.domain.SensorMetric;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DeviceContracts {
    private DeviceContracts() { }
    public record SensorView(UUID id, UUID zoneId, UUID deviceId, SensorMetric metric, String name,
                             String externalKey, int slotIndex, String bindingStatus, boolean enabled,
                             BigDecimal lastValue, String lastUnit, String lastQuality,
                             Instant lastObservedAt, Instant lastReceivedAt) { }
    public record DeviceView(UUID id, UUID zoneId, String name, DeviceType type,
                             Instant lastSeenAt, String firmwareVersion, List<SensorView> sensors) { }
}
