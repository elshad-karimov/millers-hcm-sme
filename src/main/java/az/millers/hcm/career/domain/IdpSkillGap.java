package az.millers.hcm.career.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity
@Table(schema = "learning", name = "idp_skill_gap")
@Getter @Setter
public class IdpSkillGap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idp_id", nullable = false)
    private Idp idp;

    @Column(name = "competency_id")
    private UUID competencyId;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    @Column(name = "current_level")
    private Short currentLevel;

    @Column(name = "target_level")
    private Short targetLevel;

    @Column
    private String notes;
}
