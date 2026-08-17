package com.xianzhi.fridge.shared.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "user_id") private UUID userId;
    @Column(name = "event_type", nullable = false, length = 80) private String eventType;
    @Column(name = "trace_id", nullable = false, length = 64) private String traceId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "metadata_json", columnDefinition = "json") private String metadataJson;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected AuditLog() { }
    public AuditLog(UUID id, UUID userId, String eventType, String traceId, String metadataJson) {
        this.id = id; this.userId = userId; this.eventType = eventType; this.traceId = traceId; this.metadataJson = metadataJson;
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); }
}
