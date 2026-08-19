package com.xianzhi.fridge.telemetry.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xianzhi.fridge.fridge.domain.DeviceType;
import com.xianzhi.fridge.fridge.infrastructure.Device;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.telemetry.application.MqttAccessService;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MqttInternalControllerTest {
    private static final String SERVICE_USERNAME = "service";
    private static final String SERVICE_PASSWORD = "service-password";
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final TelemetryProperties properties = new TelemetryProperties();
    private final UUID deviceId = UUID.randomUUID();
    private MqttAccessService access;

    @BeforeEach
    void setUp() {
        properties.setServiceUsername(SERVICE_USERNAME);
        properties.setServicePassword(SERVICE_PASSWORD);
        access = new MqttAccessService(devices, properties);
    }

    @Test
    void internalServiceCanSubscribeAndPublishOnlyToActiveVirtualProbeTopics() {
        Device virtualProbe = new Device(deviceId, UUID.randomUUID(), UUID.randomUUID(), "virtual", DeviceType.VIRTUAL);
        when(devices.findById(deviceId)).thenReturn(Optional.of(virtualProbe));

        assertThat(access.authenticate(SERVICE_USERNAME, "simulator", SERVICE_PASSWORD)).isTrue();
        assertThat(access.authorize(SERVICE_USERNAME, "simulator", "subscribe", "smart-fridge/v1/+/telemetry")).isTrue();
        assertThat(access.authorize(SERVICE_USERNAME, "simulator", "publish", "smart-fridge/v1/" + deviceId + "/telemetry")).isTrue();
        assertThat(access.authorize(SERVICE_USERNAME, "simulator", "publish", "smart-fridge/v1/not-a-device/telemetry")).isFalse();
    }

    @Test
    void allUserSuppliedMqttCredentialsAreDenied() {
        assertThat(access.authenticate("device-user", "device-client", "device-password")).isFalse();
        assertThat(access.authorize("device-user", "device-client", "publish", "smart-fridge/v1/" + deviceId + "/telemetry")).isFalse();
    }
}
