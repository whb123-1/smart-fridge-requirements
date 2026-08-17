package com.xianzhi.fridge.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "refresh_session")
public class RefreshSession {
    @Id
    @JdbcTypeCode(Types.BINARY)
    private UUID id;
    @JdbcTypeCode(Types.BINARY)
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String tokenHash;
    @JdbcTypeCode(Types.BINARY)
    @Column(name = "family_id", nullable = false)
    private UUID familyId;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @JdbcTypeCode(Types.BINARY)
    @Column(name = "replaced_by")
    private UUID replacedBy;
    @Column(name = "ip_hash", length = 64, columnDefinition = "char(64)")
    private String ipHash;
    @Column(name = "user_agent", length = 512)
    private String userAgent;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RefreshSession() { }

    public RefreshSession(UUID id, UUID userId, String tokenHash, UUID familyId, Instant expiresAt,
                          String ipHash, String userAgent) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
    }

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public boolean isUsableAt(Instant time) { return revokedAt == null && expiresAt.isAfter(time); }
    public boolean isRevoked() { return revokedAt != null; }
    public void revoke(Instant time, UUID replacement) { this.revokedAt = time; this.replacedBy = replacement; }
}
