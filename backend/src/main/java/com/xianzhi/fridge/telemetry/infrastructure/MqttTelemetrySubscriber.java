package com.xianzhi.fridge.telemetry.infrastructure;

import com.xianzhi.fridge.telemetry.application.TelemetryIngestionService;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@Profile("api")
@ConditionalOnProperty(prefix = "app.telemetry", name = "enabled", havingValue = "true")
public class MqttTelemetrySubscriber {
    private static final Logger log = LoggerFactory.getLogger(MqttTelemetrySubscriber.class);
    private static final String TOPIC = "smart-fridge/v1/+/telemetry";
    private final TelemetryProperties properties;
    private final TelemetryIngestionService ingestion;
    private final MeterRegistry meters;
    private MqttAsyncClient client;
    public MqttTelemetrySubscriber(TelemetryProperties properties, TelemetryIngestionService ingestion, MeterRegistry meters) {
        this.properties = properties; this.ingestion = ingestion; this.meters=meters;
        meters.gauge("xianzhi.mqtt.connected",this,value->value.connected()?1:0);
    }
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        connectIfNeeded();
    }
    @Scheduled(fixedDelayString = "${app.telemetry.reconnect-delay:PT10S}", initialDelayString = "PT10S")
    public void reconnect() {
        connectIfNeeded();
    }
    private synchronized void connectIfNeeded() {
        if (client != null && client.isConnected()) return;
        closeClient();
        try {
            connectAndSubscribe();
            log.info("MQTT telemetry subscriber connected to {}", properties.getBrokerUrl());
        } catch (MqttException | RuntimeException exception) {
            meters.counter("xianzhi.mqtt.connection.failures").increment();
            log.warn("MQTT telemetry subscriber will retry after connection failure: {}", exception.getMessage());
            closeClient();
        }
    }
    private void connectAndSubscribe() throws MqttException {
        client = new MqttAsyncClient(properties.getBrokerUrl(), "xianzhi-api-telemetry", new MemoryPersistence());
        client.setCallback(new MqttCallback() {
            @Override public void disconnected(MqttDisconnectResponse response) { log.warn("MQTT telemetry subscriber disconnected: {}", response.getReasonString()); }
            @Override public void mqttErrorOccurred(MqttException exception) { log.warn("MQTT telemetry subscriber error: {}", exception.getMessage()); }
            @Override public void deliveryComplete(IMqttToken token) { }
            @Override public void connectComplete(boolean reconnect, String serverUri) { log.info("MQTT connection completed for {}, reconnect={}", serverUri, reconnect); }
            @Override public void authPacketArrived(int reasonCode, MqttProperties properties) { }
            @Override public void messageArrived(String topic, MqttMessage message) { handleMessage(topic, message); }
        });
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setUserName(properties.getServiceUsername());
        options.setPassword(properties.getServicePassword().getBytes(StandardCharsets.UTF_8));
        options.setAutomaticReconnect(true); options.setCleanStart(false); options.setSessionExpiryInterval(86400L);
        client.connect(options).waitForCompletion();
        client.setManualAcks(true);
        client.subscribe(new MqttSubscription(TOPIC, 1)).waitForCompletion();
    }
    private void handleMessage(String topic, MqttMessage message) {
        try {
            String[] segments = topic.split("/");
            if (segments.length == 4) ingestion.ingest(UUID.fromString(segments[2]), new String(message.getPayload(), StandardCharsets.UTF_8));
            client.messageArrivedComplete(message.getId(), message.getQos());
        } catch (Exception exception) {
            log.warn("MQTT telemetry message rejected before acknowledgement: {}", exception.getMessage());
        }
    }
    @PreDestroy
    public synchronized void stop() {
        closeClient();
    }
    private void closeClient() {
        if (client == null) return;
        try {
            if (client.isConnected()) client.disconnect().waitForCompletion();
            client.close();
        } catch (MqttException exception) {
            log.debug("MQTT disconnect failed", exception);
        } finally {
            client = null;
        }
    }
    public synchronized boolean connected(){return client!=null&&client.isConnected();}
}
