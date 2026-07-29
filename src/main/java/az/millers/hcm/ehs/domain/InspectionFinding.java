package az.millers.hcm.ehs.domain;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "inspection_finding", schema = "ehs")
@Data
public class InspectionFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "inspection_id", nullable = false)
    private UUID inspectionId;

    @Column(name = "item_label", nullable = false, length = 300)
    private String itemLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "finding_status", nullable = false, length = 20)
    private FindingStatus findingStatus;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "corrective_action_id")
    private UUID correctiveActionId;
}
