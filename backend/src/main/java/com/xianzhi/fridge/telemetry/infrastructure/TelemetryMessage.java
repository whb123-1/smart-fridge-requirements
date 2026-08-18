package com.xianzhi.fridge.telemetry.infrastructure;

import com.xianzhi.fridge.telemetry.domain.ReadingSource;
import com.xianzhi.fridge.telemetry.domain.TelemetryResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "telemetry_message")
public class TelemetryMessage {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "device_id", nullable = false) private UUID deviceId;
    @Column(name = "message_id", nullable = false, length = 36, columnDefinition = "char(36)") private String messageId;
    @Column(name = "observed_at", nullable = false) private Instant observedAt;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "firmware_version", length = 64) private String firmwareVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ReadingSource source;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TelemetryResult status;
    @Column(name = "rejection_code", length = 64) private String rejectionCode;
    @Column(name = "payload_hash", nullable = false, length = 64, columnDefinition = "char(64)") private String payloadHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "payload_json", nullable = false, columnDefinition = "json") private String payloadJson;
    protected TelemetryMessage() { }
    public TelemetryMessage(UUID id, UUID deviceId, String messageId, Instant observedAt, Instant receivedAt,
                            String firmwareVersion, ReadingSource source, TelemetryResult status,
                            String rejectionCode, String payloadHash, String payloadJson) {
        this.id = id; this.deviceId = deviceId; this.messageId = messageId; this.observedAt = observedAt;
        this.receivedAt = receivedAt; this.firmwareVersion = firmwareVersion; this.source = source;
        this.status = status; this.rejectionCode = rejectionCode; this.payloadHash = payloadHash; this.payloadJson = payloadJson;
    }
    public UUID getId() { return id; }
}
