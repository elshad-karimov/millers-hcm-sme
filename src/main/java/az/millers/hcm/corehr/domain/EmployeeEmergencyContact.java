package az.millers.hcm.corehr.domain;

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
 * Next-of-kin contact for an employee (M63 / P1-06).
 *
 * <p>Flat list — emergency contacts are point-in-time references, not
 * effective-dated. The partial unique index in V51 enforces at most one
 * {@code is_primary = TRUE} row per employee.
 */
@Entity
@Table(name = "employee_emergency_contact", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeEmergencyContact {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EmergencyRelationship relationship;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(name = "alt_phone", length = 40)
    private String altPhone;

    @Column(length = 160)
    private String email;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    /** Lower value = higher priority. Default 100 leaves room above/below. */
    @Column(name = "priority_order", nullable = false)
    private int priorityOrder = 100;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
