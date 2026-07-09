package az.millers.hcm.compliance.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M470 — Compliance deadline (recurring monthly/quarterly/annual).
 */
@Entity
@Table(name = "compliance_deadline", schema = "compliance")
@Getter
@Setter
@NoArgsConstructor
public class ComplianceDeadline {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "template_id")
    private UUID templateId;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeadlineFrequency frequency;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column
    private Integer month;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }

    public enum DeadlineFrequency {
        MONTHLY,
        QUARTERLY,
        ANNUAL
    }
}
