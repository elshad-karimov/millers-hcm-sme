package az.millers.hcm.performance.domain;

import java.math.BigDecimal;
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

/** HCM_12 M389 — one section of a {@link PerfReviewTemplate} with its §18.1 weight. */
@Entity
@Table(name = "perf_template_section", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class PerfTemplateSection {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type", nullable = false, length = 40)
    private PerfSectionType sectionType;

    @Column(name = "section_order", nullable = false)
    private int sectionOrder;

    @Column(length = 160)
    private String title;

    /** Weight in the §18.2 overall score; informational sections use 0. */
    @Column(name = "weight_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPercent = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean required = true;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
