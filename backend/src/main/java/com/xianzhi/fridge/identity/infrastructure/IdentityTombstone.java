package com.xianzhi.fridge.identity.infrastructure;

import jakarta.persistence.*;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "identity_tombstone")
public class IdentityTombstone {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "username_hmac", nullable = false, length = 64, columnDefinition = "char(64)") private String usernameHmac;
    @Column(name = "email_hmac", nullable = false, length = 64, columnDefinition = "char(64)") private String emailHmac;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected IdentityTombstone() { }
    public IdentityTombstone(UUID id, UUID userId, String usernameHmac, String emailHmac, Instant createdAt) {
        this.id=id; this.userId=userId; this.usernameHmac=usernameHmac; this.emailHmac=emailHmac; this.createdAt=createdAt;
    }
}
