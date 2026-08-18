package com.xianzhi.fridge.notification.infrastructure;

import jakarta.persistence.*;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity @Table(name="notification_preference")
public class NotificationPreference {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name="user_id",nullable=false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(name="notification_type",nullable=false,length=32) private NotificationType type;
    @Column(name="in_app_enabled",nullable=false) private boolean inAppEnabled;
    @Column(name="email_enabled",nullable=false) private boolean emailEnabled;
    @Column(name="quiet_start") private LocalTime quietStart;
    @Column(name="quiet_end") private LocalTime quietEnd;
    @Column(nullable=false,length=64) private String timezone;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
    protected NotificationPreference() { }
    public NotificationPreference(UUID id,UUID userId,NotificationType type,String timezone,Instant now){this.id=id;this.userId=userId;this.type=type;this.timezone=timezone;this.inAppEnabled=true;this.createdAt=this.updatedAt=now;}
    public void update(boolean inApp,boolean email,LocalTime start,LocalTime end,String timezone,Instant now){this.inAppEnabled=inApp;this.emailEnabled=email;this.quietStart=start;this.quietEnd=end;this.timezone=timezone;this.updatedAt=now;}
    public UUID getId(){return id;} public UUID getUserId(){return userId;} public NotificationType getType(){return type;} public boolean isInAppEnabled(){return inAppEnabled;} public boolean isEmailEnabled(){return emailEnabled;} public LocalTime getQuietStart(){return quietStart;} public LocalTime getQuietEnd(){return quietEnd;} public String getTimezone(){return timezone;}
}
