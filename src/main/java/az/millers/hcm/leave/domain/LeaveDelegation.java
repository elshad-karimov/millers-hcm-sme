package az.millers.hcm.leave.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.TenantId;

@Entity
@Table(schema = "leave_mgmt", name = "leave_delegation")
@Getter @Setter
public class LeaveDelegation {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "leave_request_id", nullable = false)
    private UUID leaveRequestId;

    @Column(name = "delegator_id", nullable = false)
    private UUID delegatorId;

    @Column(name = "delegate_id", nullable = false)
    private UUID delegateId;

    @Column(name = "delegation_scope", columnDefinition = "TEXT")
    private String delegationScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DelegationStatus status = DelegationStatus.PENDING;

    @Column(name = "delegate_notes", columnDefinition = "TEXT")
    private String delegateNotes;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 160)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
