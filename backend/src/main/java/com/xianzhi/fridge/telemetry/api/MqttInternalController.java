package com.xianzhi.fridge.telemetry.api;

import com.xianzhi.fridge.telemetry.application.MqttAccessService;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("api")
@RequestMapping("/internal/mqtt")
public class MqttInternalController {
    private final MqttAccessService access;
    private final TelemetryProperties properties;
    public MqttInternalController(MqttAccessService access, TelemetryProperties properties) {
        this.access = access; this.properties = properties;
    }
    @PostMapping("/authenticate")
    public Map<String, Object> authenticate(@RequestHeader(value = "X-Internal-Key", required = false) String token,
                                            @RequestBody AuthenticationRequest request) {
        internal(token); boolean allowed = access.authenticate(request.username(), request.clientid(), request.password());
        return Map.of("result", allowed ? "allow" : "deny", "is_superuser", false);
    }
    @PostMapping("/authorize")
    public Map<String, Object> authorize(@RequestHeader(value = "X-Internal-Key", required = false) String token,
                                         @RequestBody AuthorizationRequest request) {
        internal(token); boolean allowed = access.authorize(
                request.username(), request.clientid(), request.action(), request.topic());
        return Map.of("result", allowed ? "allow" : "deny");
    }
    private void internal(String actual) { if (!secure(properties.getInternalToken(), actual)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    private static boolean secure(String expected, String actual) { return expected != null && actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)); }
    public record AuthenticationRequest(String username, String clientid, String password) { }
    public record AuthorizationRequest(String username, String clientid, String action, String topic) { }
}
