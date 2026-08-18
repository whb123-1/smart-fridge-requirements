package com.xianzhi.fridge.speech.infrastructure;

import com.xianzhi.fridge.speech.domain.VoiceStatus;
import jakarta.persistence.*;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "voice_ingestion")
public class VoiceIngestion {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name="user_id", nullable=false) private UUID userId;
    @JdbcTypeCode(Types.BINARY) @Column(name="fridge_id", nullable=false) private UUID fridgeId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private VoiceStatus status;
    @Column(name="object_key", nullable=false, length=512) private String objectKey;
    @Column(name="original_filename", length=255) private String originalFilename;
    @Column(name="content_type", nullable=false, length=96) private String contentType;
    @Column(name="content_length", nullable=false) private long contentLength;
    @Column(name="transcript_text", columnDefinition="text") private String transcriptText;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="draft_json", columnDefinition="json") private String draftJson;
    @Column(name="failure_code", length=64) private String failureCode;
    @Column(name="failure_reason", length=1000) private String failureReason;
    @Column(name="expires_at", nullable=false) private Instant expiresAt;
    @Column(name="confirmed_at") private Instant confirmedAt;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version private long version;
    protected VoiceIngestion() { }
    public VoiceIngestion(UUID id, UUID userId, UUID fridgeId, String objectKey, String filename, String contentType, long length, Instant now) {
        this.id=id; this.userId=userId; this.fridgeId=fridgeId; this.objectKey=objectKey; this.originalFilename=filename;
        this.contentType=contentType; this.contentLength=length; this.status=VoiceStatus.UPLOADED;
        this.expiresAt=now.plus(java.time.Duration.ofHours(72)); this.createdAt=this.updatedAt=now;
    }
    public void transcribing(Instant now) { status=VoiceStatus.TRANSCRIBING; updatedAt=now; }
    public void ready(String transcript, String draft, Instant now) { transcriptText=transcript; draftJson=draft; status=VoiceStatus.READY; updatedAt=now; }
    public void fail(String code, String reason, Instant now) { failureCode=code; failureReason=reason; status=VoiceStatus.FAILED; updatedAt=now; }
    public void confirm(Instant now) { status=VoiceStatus.CONFIRMED; confirmedAt=now; expiresAt=now.plus(java.time.Duration.ofHours(24)); updatedAt=now; }
    public void expire(Instant now) { status=VoiceStatus.EXPIRED; updatedAt=now; }
    public UUID getId(){return id;} public UUID getUserId(){return userId;} public UUID getFridgeId(){return fridgeId;}
    public VoiceStatus getStatus(){return status;} public String getObjectKey(){return objectKey;} public String getTranscriptText(){return transcriptText;}
    public String getDraftJson(){return draftJson;} public String getFailureCode(){return failureCode;} public String getFailureReason(){return failureReason;}
    public Instant getExpiresAt(){return expiresAt;} public Instant getConfirmedAt(){return confirmedAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
