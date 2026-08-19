package com.xianzhi.fridge.telemetry.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xianzhi.fridge.fridge.domain.DeviceType;
import com.xianzhi.fridge.fridge.infrastructure.Device;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.identity.infrastructure.AppUser;
import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.identity.domain.UserStatus;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import com.xianzhi.fridge.telemetry.application.MqttAccessService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class MqttInternalControllerTest {
    private static final String INTERNAL_KEY = "internal-test-key";
    private static final String MQTT_USERNAME = "device-user";
    private static final String MQTT_CLIENT_ID = "device-client";
    private static final String MQTT_PASSWORD = "device-password";

    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final TelemetryProperties properties = new TelemetryProperties();
    private final UUID userId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final Device device = new Device(deviceId, userId, UUID.randomUUID(), "Test device", DeviceType.PHYSICAL,
            MQTT_CLIENT_ID, MQTT_USERNAME, "credential-hash");
    private MqttAccessService access;

    @BeforeEach
    void setUp() {
        properties.setInternalToken(INTERNAL_KEY);
        access = new MqttAccessService(devices, users, passwords, properties);
        when(devices.findByMqttUsernameAndDeletedAtIsNull(MQTT_USERNAME)).thenReturn(Optional.of(device));
    }

    @Test
    void activeUserDeviceCanAuthenticateAndPublishToItsOwnTopic() {
        AppUser activeUser = user();
        when(users.findById(userId)).thenReturn(Optional.of(activeUser));
        when(passwords.matches(MQTT_PASSWORD, "credential-hash")).thenReturn(true);

        boolean authentication = access.authenticate(MQTT_USERNAME, MQTT_CLIENT_ID, MQTT_PASSWORD);
        boolean authorization = access.authorize(MQTT_USERNAME, MQTT_CLIENT_ID, "publish",
                "smart-fridge/v1/" + deviceId + "/telemetry");

        assertThat(authentication).isTrue();
        assertThat(authorization).isTrue();
    }

    @Test
    void disabledUserDeviceIsDeniedAuthenticationAndAuthorization() {
        AppUser disabledUser = user();
        disabledUser.changeStatus(UserStatus.DISABLED);
        assertDeviceAccessDenied(disabledUser);
    }

    @Test
    void softDeletedUserDeviceIsDeniedAuthenticationAndAuthorization() {
        AppUser deletedUser = user();
        deletedUser.softDelete(Instant.parse("2026-08-19T00:00:00Z"));
        assertDeviceAccessDenied(deletedUser);
    }

    @Test
    void anonymizedUserDeviceIsDeniedAuthenticationAndAuthorization() {
        AppUser anonymizedUser = user();
        anonymizedUser.anonymize("anonymous-user", "anonymous@example.invalid", "Anonymous",
                Instant.parse("2026-08-19T00:00:00Z"));
        assertDeviceAccessDenied(anonymizedUser);
    }

    private void assertDeviceAccessDenied(AppUser unavailableUser) {
        when(users.findById(userId)).thenReturn(Optional.of(unavailableUser));

        boolean authentication = access.authenticate(MQTT_USERNAME, MQTT_CLIENT_ID, MQTT_PASSWORD);
        boolean authorization = access.authorize(MQTT_USERNAME, MQTT_CLIENT_ID, "publish",
                "smart-fridge/v1/" + deviceId + "/telemetry");

        assertThat(authentication).isFalse();
        assertThat(authorization).isFalse();
        verifyNoInteractions(passwords);
    }

    private AppUser user() {
        return new AppUser(userId, "mqtt-owner", "mqtt-owner@example.com", "password-hash", "MQTT owner", "UTC");
    }
}
