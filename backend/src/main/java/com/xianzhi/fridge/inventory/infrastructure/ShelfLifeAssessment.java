package com.xianzhi.fridge.inventory.infrastructure;

import com.xianzhi.fridge.inventory.domain.AssessmentSource;
import com.xianzhi.fridge.inventory.domain.AssessmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "shelf_life_assessment")
public class ShelfLifeAssessment {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "batch_id", nullable = false) private UUID batchId;
    @Column(name = "profile_version") private Integer profileVersion;
    @Column(name = "estimated_expiry_at") private Instant estimatedExpiryAt;
    @Column(name = "base_expiry_at") private Instant baseExpiryAt;
    @Column(name = "cumulative_risk_minutes", nullable = false, precision = 14, scale = 3) private BigDecimal cumulativeRiskMinutes;
    @Enumerated(EnumType.STRING) @Column(name = "estimation_source", nullable = false, length = 32) private AssessmentSource estimationSource;
    @Column(nullable = false, length = 24) private String confidence;
    @Enumerated(EnumType.STRING) @Column(name = "safety_status", nullable = false, length = 32) private AssessmentStatus safetyStatus;
    @Column(nullable = false, length = 512) private String explanation;
    @Column(name = "calculated_at", nullable = false) private Instant calculatedAt;
    protected ShelfLifeAssessment() { }
    public ShelfLifeAssessment(UUID id, UUID batchId, Integer profileVersion, Instant estimatedExpiryAt,
                               Instant baseExpiryAt, AssessmentSource source, String confidence,
                               AssessmentStatus status, String explanation) {
        this.id = id; this.batchId = batchId; this.profileVersion = profileVersion;
        this.estimatedExpiryAt = estimatedExpiryAt; this.baseExpiryAt = baseExpiryAt; this.cumulativeRiskMinutes = BigDecimal.ZERO;
        this.estimationSource = source; this.confidence = confidence; this.safetyStatus = status;
        this.explanation = explanation; this.calculatedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getBatchId() { return batchId; }
    public Integer getProfileVersion() { return profileVersion; }
    public Instant getEstimatedExpiryAt() { return estimatedExpiryAt; }
    public Instant getBaseExpiryAt() { return baseExpiryAt; }
    public BigDecimal getCumulativeRiskMinutes() { return cumulativeRiskMinutes; }
    public AssessmentSource getEstimationSource() { return estimationSource; }
    public String getConfidence() { return confidence; }
    public AssessmentStatus getSafetyStatus() { return safetyStatus; }
    public String getExplanation() { return explanation; }
    public Instant getCalculatedAt() { return calculatedAt; }
}
