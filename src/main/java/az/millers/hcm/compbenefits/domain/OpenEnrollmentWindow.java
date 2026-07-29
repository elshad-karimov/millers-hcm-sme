package az.millers.hcm.compbenefits.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/** HCM_11 M379 — annual open-enrollment window for a plan year. */
@Entity
@Table(name = "open_enrollment_window", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class OpenEnrollmentWindow {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "plan_year", nullable = false)
    private int planYear;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by", length = 80)
    private String createdBy;

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

    public boolean isOpenOn(LocalDate date) {
        return active && !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
