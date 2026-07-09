package az.millers.hcm.engagement.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * M477 — Pulse survey schedule for recurring campaign creation.
 */
@Entity
@Table(name = "pulse_schedule", schema = "engagement")
public class PulseSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "survey_template_id", nullable = false)
    private UUID surveyTemplateId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PulseFrequency frequency;

    @Column(name = "day_of_week")
    private Integer dayOfWeek; // 1=Mon..7=Sun

    @Column(name = "day_of_month")
    private Integer dayOfMonth; // 1-28

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public UUID getSurveyTemplateId() { return surveyTemplateId; }
    public void setSurveyTemplateId(UUID surveyTemplateId) { this.surveyTemplateId = surveyTemplateId; }

    public PulseFrequency getFrequency() { return frequency; }
    public void setFrequency(PulseFrequency frequency) { this.frequency = frequency; }

    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public OffsetDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(OffsetDateTime lastRunAt) { this.lastRunAt = lastRunAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
