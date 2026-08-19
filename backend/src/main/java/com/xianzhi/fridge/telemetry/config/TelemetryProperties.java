package com.xianzhi.fridge.telemetry.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.telemetry")
public class TelemetryProperties {
    private String brokerUrl = "tcp://localhost:1883";
    private String serviceUsername = "service";
    private String servicePassword = "change-me";
    private String internalToken = "replace-with-an-internal-development-token";
    private Duration emitInterval = Duration.ofMinutes(1);
    private boolean enabled;
    private boolean virtualSimulatorEnabled;

    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }
    public String getServiceUsername() { return serviceUsername; }
    public void setServiceUsername(String serviceUsername) { this.serviceUsername = serviceUsername; }
    public String getServicePassword() { return servicePassword; }
    public void setServicePassword(String servicePassword) { this.servicePassword = servicePassword; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
    public Duration getEmitInterval() { return emitInterval; }
    public void setEmitInterval(Duration emitInterval) { this.emitInterval = emitInterval; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isVirtualSimulatorEnabled() { return virtualSimulatorEnabled; }
    public void setVirtualSimulatorEnabled(boolean enabled) { this.virtualSimulatorEnabled = enabled; }

}
