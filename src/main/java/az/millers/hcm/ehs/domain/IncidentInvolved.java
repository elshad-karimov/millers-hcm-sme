package az.millers.hcm.ehs.domain;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "incident_involved", schema = "ehs")
@Data
public class IncidentInvolved {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
}
