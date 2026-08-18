package com.xianzhi.fridge.notification.infrastructure;

import jakarta.persistence.*;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity @Table(name="notification_delivery")
public class NotificationDelivery {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name="notification_id",nullable=false) private UUID notificationId;
    @Column(nullable=false,length=16) private String channel;
    @Column(nullable=false,length=24) private String status;
    @Column(nullable=false) private int attempts;
    @Column(name="available_at",nullable=false) private Instant availableAt;
    @Column(name="last_attempt_at") private Instant lastAttemptAt;
    @Column(name="delivered_at") private Instant deliveredAt;
    @Column(name="failure_reason",length=1000) private String failureReason;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected NotificationDelivery() { }
    public NotificationDelivery(UUID id,UUID notificationId,String channel,String status,Instant now){this.id=id;this.notificationId=notificationId;this.channel=channel;this.status=status;this.availableAt=this.createdAt=this.updatedAt=now;}
    public void delivered(Instant now){status="DELIVERED";deliveredAt=now;updatedAt=now;} public void skipped(String reason,Instant now){status="SKIPPED";failureReason=reason;updatedAt=now;}
    public void postpone(Instant available,Instant now){availableAt=available;updatedAt=now;}
    public void failed(RuntimeException error,Instant now){attempts++;lastAttemptAt=now;failureReason=(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());status=attempts>=5?"FAILED":"PENDING";availableAt=now.plusSeconds(Math.min(300,1L<<attempts));updatedAt=now;}
    public UUID getId(){return id;} public UUID getNotificationId(){return notificationId;} public String getChannel(){return channel;} public String getStatus(){return status;}
}
