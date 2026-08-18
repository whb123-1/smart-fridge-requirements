package com.xianzhi.fridge.identity.infrastructure;

import com.xianzhi.fridge.identity.domain.TemperatureUnit;
import com.xianzhi.fridge.identity.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @JdbcTypeCode(Types.BINARY)
    private UUID id;
    @Column(nullable = false, unique = true, length = 320)
    private String email;
    @Column(nullable = false, unique = true, length = 32)
    private String username;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;
    @Column(nullable = false, length = 64)
    private String timezone;
    @Enumerated(EnumType.STRING)
    @Column(name = "temperature_unit", nullable = false, length = 1)
    private TemperatureUnit temperatureUnit;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private UserStatus status;
    @Column(nullable = false, length = 16)
    private String role = "USER";
    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Version
    private long version;

    protected AppUser() { }

    public AppUser(UUID id, String username, String email, String passwordHash, String displayName, String timezone) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.timezone = timezone;
        this.temperatureUnit = TemperatureUnit.C;
        this.status = UserStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getTimezone() { return timezone; }
    public TemperatureUnit getTemperatureUnit() { return temperatureUnit; }
    public UserStatus getStatus() { return status; }
    public String getRole() { return role; }
    public Instant getOnboardingCompletedAt() { return onboardingCompletedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public boolean onboardingRequired() { return onboardingCompletedAt == null; }
    public void completeOnboarding(Instant completedAt) { this.onboardingCompletedAt = completedAt; }
    public void updateProfile(String newUsername, String newDisplayName, String newTimezone,
                              TemperatureUnit newTemperatureUnit) {
        this.username = newUsername;
        this.displayName = newDisplayName;
        this.timezone = newTimezone;
        this.temperatureUnit = newTemperatureUnit;
    }
    public void changePassword(String newPasswordHash) { this.passwordHash = newPasswordHash; }
}
