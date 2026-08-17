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
@Table(name = "idempotency_record")
public class IdempotencyRecord {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)") private String requestHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "response_body", nullable = false, columnDefinition = "json") private String responseBody;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;

    protected IdempotencyRecord() { }
    public IdempotencyRecord(UUID id, UUID userId, String key, String requestHash, String responseBody, Instant expiresAt) {
        this.id = id; this.userId = userId; this.idempotencyKey = key; this.requestHash = requestHash;
        this.responseBody = responseBody; this.expiresAt = expiresAt;
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); }
    public String getRequestHash() { return requestHash; }
    public String getResponseBody() { return responseBody; }
}
