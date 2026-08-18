package com.xianzhi.fridge.shared.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 64) private String aggregateType;
    @JdbcTypeCode(Types.BINARY) @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 96) private String eventType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "payload_json", nullable = false, columnDefinition = "json") private String payloadJson;
    @Column(nullable = false, length = 24) private String status = "PENDING";
    @Column(nullable = false) private int attempts;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "locked_at") private Instant lockedAt;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected OutboxEvent() { }
    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, String payloadJson) {
        this.id = id; this.aggregateType = aggregateType; this.aggregateId = aggregateId; this.eventType = eventType;
        this.payloadJson = payloadJson; this.availableAt = this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getPayloadJson() { return payloadJson; }
}
