package az.millers.hcm.performance.domain;

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

/** HCM_12 M400 — a 1:1 / monthly / quarterly check-in (PRD §22.2). */
@Entity
@Table(name = "perf_check_in", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class PerfCheckIn {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    /** ONE_TO_ONE | MONTHLY | QUARTERLY */
    @Column(nullable = false, length = 20)
    private String kind = "ONE_TO_ONE";

    @Column(name = "discussion_notes", columnDefinition = "text")
    private String discussionNotes;

    @Column(name = "action_items", columnDefinition = "text")
    private String actionItems;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

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
}
