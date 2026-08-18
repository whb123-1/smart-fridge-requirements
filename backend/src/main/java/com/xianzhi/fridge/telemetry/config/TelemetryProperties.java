package com.xianzhi.fridge.telemetry.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.telemetry")
public class TelemetryProperties {
    private String brokerUrl = "tcp://localhost:1883";
    private String publicBrokerUrl = "tcp://localhost:1883";
    private String serviceUsername = "service";
    private String servicePassword = "change-me";
    private String simulatorUsername = "simulator";
    private String simulatorPassword = "change-me-simulator";
    private String internalToken = "replace-with-an-internal-development-token";
    private String debugTestOperatorUsernames = "";
    private Duration emitInterval = Duration.ofMinutes(1);
    private boolean enabled;

    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }
    public String getPublicBrokerUrl() { return publicBrokerUrl; }
    public void setPublicBrokerUrl(String publicBrokerUrl) { this.publicBrokerUrl = publicBrokerUrl; }
    public String getServiceUsername() { return serviceUsername; }
    public void setServiceUsername(String serviceUsername) { this.serviceUsername = serviceUsername; }
    public String getServicePassword() { return servicePassword; }
    public void setServicePassword(String servicePassword) { this.servicePassword = servicePassword; }
    public String getSimulatorUsername() { return simulatorUsername; }
    public void setSimulatorUsername(String simulatorUsername) { this.simulatorUsername = simulatorUsername; }
    public String getSimulatorPassword() { return simulatorPassword; }
    public void setSimulatorPassword(String simulatorPassword) { this.simulatorPassword = simulatorPassword; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
    public String getDebugTestOperatorUsernames() { return debugTestOperatorUsernames; }
    public void setDebugTestOperatorUsernames(String value) { this.debugTestOperatorUsernames = value; }
    public Duration getEmitInterval() { return emitInterval; }
    public void setEmitInterval(Duration emitInterval) { this.emitInterval = emitInterval; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Set<String> debugOperators() {
        return Arrays.stream(debugTestOperatorUsernames.split(","))
                .map(String::trim).map(String::toLowerCase).filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
