package az.millers.hcm.engagement.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * One submitted survey response (M116). {@link #employeeId} is null when
 * the parent template is flagged anonymous.
 */
@Entity
@Table(name = "survey_response", schema = "engagement")
@Getter
@Setter
@NoArgsConstructor
public class SurveyResponse {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    /** Nullable — anonymous responses store {@code null}. */
    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(columnDefinition = "text")
    private String comment;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (submittedAt == null) submittedAt = OffsetDateTime.now();
    }
}
