package com.xianzhi.fridge.telemetry.api;

import com.xianzhi.fridge.fridge.domain.DeviceStatus;
import com.xianzhi.fridge.fridge.domain.DeviceType;
import com.xianzhi.fridge.fridge.infrastructure.Device;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("api")
@RequestMapping("/internal/mqtt")
public class MqttInternalController {
    private static final Logger log = LoggerFactory.getLogger(MqttInternalController.class);
    private final DeviceRepository devices; private final PasswordEncoder passwords; private final TelemetryProperties properties;
    public MqttInternalController(DeviceRepository devices, PasswordEncoder passwords, TelemetryProperties properties) {
        this.devices = devices; this.passwords = passwords; this.properties = properties;
    }
    @PostMapping("/authenticate")
    public Map<String, Object> authenticate(@RequestHeader(value = "X-Internal-Key", required = false) String token,
                                            @RequestBody AuthenticationRequest request) {
        internal(token); boolean allowed = service(request) || simulator(request) || device(request);
        return Map.of("result", allowed ? "allow" : "deny", "is_superuser", false);
    }
    @PostMapping("/authorize")
    public Map<String, Object> authorize(@RequestHeader(value = "X-Internal-Key", required = false) String token,
                                         @RequestBody AuthorizationRequest request) {
        internal(token); String action = request.action() == null ? "" : request.action().toLowerCase();
        boolean allowed = false;
        if (properties.getServiceUsername().equals(request.username())) allowed = action.contains("subscribe") && "smart-fridge/v1/+/telemetry".equals(request.topic());
        else if (properties.getSimulatorUsername().equals(request.username())) allowed = action.contains("publish") && virtualTopic(request.topic());
        else {
            Device device = devices.findByMqttUsernameAndDeletedAtIsNull(request.username()).orElse(null);
            allowed = device != null && device.getStatus() == DeviceStatus.ACTIVE && action.contains("publish")
                    && ("smart-fridge/v1/" + device.getId() + "/telemetry").equals(request.topic());
        }
        if (!allowed) log.warn("MQTT authorization denied for username={}, clientId={}, action={}, topic={}",
                request.username(), request.clientid(), request.action(), request.topic());
        return Map.of("result", allowed ? "allow" : "deny");
    }
    private boolean service(AuthenticationRequest request) { return properties.getServiceUsername().equals(request.username()) && secure(properties.getServicePassword(), request.password()); }
    private boolean simulator(AuthenticationRequest request) { return properties.getSimulatorUsername().equals(request.username()) && secure(properties.getSimulatorPassword(), request.password()); }
    private boolean device(AuthenticationRequest request) { return devices.findByMqttUsernameAndDeletedAtIsNull(request.username()).filter(value -> value.getStatus() == DeviceStatus.ACTIVE && value.getMqttClientId().equals(request.clientid())).map(value -> passwords.matches(request.password(), value.getCredentialHash())).orElse(false); }
    private boolean virtualTopic(String topic) {
        if (topic == null || !topic.matches("smart-fridge/v1/[0-9a-fA-F-]{36}/telemetry")) return false;
        String[] parts = topic.split("/");
        try { return devices.findById(UUID.fromString(parts[2])).filter(value -> value.getType() == DeviceType.VIRTUAL && value.getStatus() == DeviceStatus.ACTIVE && value.getDeletedAt() == null).isPresent(); }
        catch (IllegalArgumentException exception) { return false; }
    }
    private void internal(String actual) { if (!secure(properties.getInternalToken(), actual)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    private static boolean secure(String expected, String actual) { return expected != null && actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)); }
    public record AuthenticationRequest(String username, String clientid, String password) { }
    public record AuthorizationRequest(String username, String clientid, String action, String topic) { }
}
