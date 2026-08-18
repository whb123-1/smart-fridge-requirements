package com.xianzhi.fridge.fridge.api;

import com.xianzhi.fridge.fridge.domain.DeviceStatus;
import com.xianzhi.fridge.fridge.domain.DeviceType;
import com.xianzhi.fridge.fridge.domain.SensorMetric;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DeviceContracts {
    private DeviceContracts() { }
    public record CreateDeviceRequest(@NotBlank @Size(max = 96) String name, @NotNull DeviceType type) { }
    public record UpdateDeviceRequest(@Size(max = 96) String name, DeviceStatus status) { }
    public record BindSensorRequest(@NotNull UUID slotId, @NotBlank @Size(max = 96) String name,
                                    @NotBlank @Size(max = 96) String externalKey) { }
    public record MqttCredential(String brokerUrl, String clientId, String username, String password,
                                 String topic, int qos, boolean retain) { }
    public record SensorView(UUID id, UUID zoneId, UUID deviceId, SensorMetric metric, String name,
                             String externalKey, int slotIndex, String bindingStatus, boolean enabled,
                             BigDecimal lastValue, String lastUnit, String lastQuality,
                             Instant lastObservedAt, Instant lastReceivedAt) { }
    public record DeviceView(UUID id, UUID zoneId, String name, DeviceType type, DeviceStatus status,
                             String mqttClientId, Instant lastSeenAt, String firmwareVersion,
                             List<SensorView> sensors, MqttCredential credential) { }
}
