package com.xianzhi.fridge.telemetry.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "batch_environment_exposure")
public class BatchEnvironmentExposure {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "batch_id", nullable = false) private UUID batchId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "incident_id", nullable = false) private UUID incidentId;
    @Column(name = "processed_until", nullable = false) private Instant processedUntil;
    @Column(name = "exposure_minutes", nullable = false, precision = 14, scale = 3) private BigDecimal exposureMinutes;
    @Column(name = "risk_minutes", nullable = false, precision = 14, scale = 3) private BigDecimal riskMinutes;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected BatchEnvironmentExposure() { }
    public BatchEnvironmentExposure(UUID id, UUID batchId, UUID incidentId, Instant processedUntil, Instant now) {
        this.id = id; this.batchId = batchId; this.incidentId = incidentId; this.processedUntil = processedUntil;
        this.exposureMinutes = BigDecimal.ZERO; this.riskMinutes = BigDecimal.ZERO; this.createdAt = this.updatedAt = now;
    }
    public void add(BigDecimal exposure, BigDecimal risk, Instant until, Instant now) {
        exposureMinutes = exposureMinutes.add(exposure); riskMinutes = riskMinutes.add(risk);
        processedUntil = until; updatedAt = now;
    }
    public UUID getId() { return id; }
    public UUID getBatchId() { return batchId; }
    public UUID getIncidentId() { return incidentId; }
    public Instant getProcessedUntil() { return processedUntil; }
    public BigDecimal getExposureMinutes() { return exposureMinutes; }
    public BigDecimal getRiskMinutes() { return riskMinutes; }
}
