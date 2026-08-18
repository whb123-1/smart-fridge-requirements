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
    @Column(name = "http_method", length = 10) private String httpMethod;
    @Column(name = "request_path", length = 255) private String requestPath;
    @Column(name = "status_code", nullable = false) private int statusCode = 200;

    protected IdempotencyRecord() { }
    public IdempotencyRecord(UUID id, UUID userId, String key, String requestHash, String responseBody, Instant expiresAt) {
        this.id = id; this.userId = userId; this.idempotencyKey = key; this.requestHash = requestHash;
        this.responseBody = responseBody; this.expiresAt = expiresAt;
    }
    public IdempotencyRecord(UUID id, UUID userId, String key, String requestHash, String responseBody,
                             Instant expiresAt, String httpMethod, String requestPath, int statusCode) {
        this(id, userId, key, requestHash, responseBody, expiresAt);
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
        this.statusCode = statusCode;
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); }
    public String getRequestHash() { return requestHash; }
    public String getResponseBody() { return responseBody; }
    public String getHttpMethod() { return httpMethod; }
    public String getRequestPath() { return requestPath; }
    public int getStatusCode() { return statusCode; }
}
