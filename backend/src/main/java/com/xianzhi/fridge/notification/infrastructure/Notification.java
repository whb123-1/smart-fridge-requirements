package com.xianzhi.fridge.notification.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "notification")
public class Notification {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "notification_type", nullable = false, length = 32) private String notificationType;
    @Column(name = "subject_type", nullable = false, length = 32) private String subjectType;
    @JdbcTypeCode(Types.BINARY) @Column(name = "subject_id", nullable = false) private UUID subjectId;
    @Column(name = "dedup_key", nullable = false, length = 160) private String dedupKey;
    @Column(nullable = false, length = 16) private String priority;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 1000) private String body;
    @Column(name = "deep_link", length = 255) private String deepLink;
    @Column(name = "in_app_visible", nullable = false) private boolean inAppVisible = true;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "read_at") private Instant readAt;
    @Column(name = "dismissed_at") private Instant dismissedAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Notification() { }
    public Notification(UUID id, UUID userId, String type, String subjectType, UUID subjectId, String dedupKey,
                        String priority, String title, String body, String deepLink, Instant now) {
        this.id = id; this.userId = userId; this.notificationType = type; this.subjectType = subjectType;
        this.subjectId = subjectId; this.dedupKey = dedupKey; this.priority = priority;
        this.title = title; this.body = body; this.deepLink = deepLink; this.createdAt = this.updatedAt = now;
    }
    public void resolve(Instant now) { if (resolvedAt == null) resolvedAt = now; updatedAt = now; }
    public void setRead(boolean read, Instant now) { readAt = read ? now : null; updatedAt = now; }
    public void setDismissed(boolean dismissed, Instant now) { dismissedAt = dismissed ? now : null; updatedAt = now; }
    public void setInAppVisible(boolean visible) { inAppVisible = visible; }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getNotificationType() { return notificationType; }
    public String getSubjectType() { return subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public String getPriority() { return priority; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getDeepLink() { return deepLink; }
    public boolean isInAppVisible() { return inAppVisible; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getReadAt() { return readAt; }
    public Instant getDismissedAt() { return dismissedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
