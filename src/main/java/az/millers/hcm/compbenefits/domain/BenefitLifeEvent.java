package az.millers.hcm.compbenefits.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * HCM_11 M380 — a qualifying life event. When APPROVED, opens a special enrollment window
 * of {@code windowDays} from {@code eventDate} during which the employee may change benefits.
 */
@Entity
@Table(name = "benefit_life_event", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class BenefitLifeEvent {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private BenefitLifeEventType eventType;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "window_days", nullable = false)
    private int windowDays = 30;

    /** PENDING | APPROVED | REJECTED | CLOSED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "reported_by", length = 80)
    private String reportedBy;

    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_notes", columnDefinition = "text")
    private String reviewNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    /** The last day of the special enrollment window this event opens. */
    public LocalDate windowEnd() {
        return eventDate.plusDays(windowDays);
    }

    /** Whether this event currently confers a special enrollment window. */
    public boolean isWindowOpenOn(LocalDate date) {
        return "APPROVED".equals(status)
                && !date.isBefore(eventDate)
                && !date.isAfter(windowEnd());
    }
}
