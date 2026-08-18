package com.xianzhi.fridge.shared.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.shared.infrastructure.OutboxStore;
import com.xianzhi.fridge.telemetry.application.EnvironmentAggregationService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxProcessor {
    private final OutboxStore outbox; private final ObjectMapper mapper; private final EnvironmentAggregationService environment; private final Clock clock;
    public OutboxProcessor(OutboxStore outbox, ObjectMapper mapper, EnvironmentAggregationService environment, Clock clock) {
        this.outbox = outbox; this.mapper = mapper; this.environment = environment; this.clock = clock;
    }
    public void processBatch() {
        for (OutboxStore.ClaimedEvent event : outbox.claim(50, clock.instant())) {
            try { handle(event); outbox.complete(event.id(), clock.instant()); }
            catch (RuntimeException exception) { outbox.fail(event, clock.instant(), exception); }
        }
    }
    private void handle(OutboxStore.ClaimedEvent event) {
        if ("SensorSuspectDetected".equals(event.eventType())) {
            try { JsonNode payload = mapper.readTree(event.payload()); environment.sensorSuspect(UUID.fromString(payload.path("sensorId").asText())); }
            catch (java.io.IOException exception) { throw new IllegalArgumentException("Invalid outbox payload", exception); }
        } else if ("SensorRecovered".equals(event.eventType())) {
            try { JsonNode payload = mapper.readTree(event.payload()); environment.sensorRecovered(UUID.fromString(payload.path("sensorId").asText())); }
            catch (java.io.IOException exception) { throw new IllegalArgumentException("Invalid outbox payload", exception); }
        }
    }
}
