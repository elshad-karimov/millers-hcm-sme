package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.domain.BenefitType;

public interface BenefitPlanRepository extends JpaRepository<BenefitPlan, UUID> {

    boolean existsByCode(String code);

    List<BenefitPlan> findAllByOrderByBenefitTypeAscNameAsc();

    List<BenefitPlan> findByActiveTrueOrderByBenefitTypeAscNameAsc();

    List<BenefitPlan> findByBenefitTypeOrderByNameAsc(BenefitType benefitType);

    // ── HCM_11 — tenant-scoped variants (GLOBAL RULE 4) ─────────────────────────
    boolean existsByTenantIdAndCode(String tenantId, String code);

    List<BenefitPlan> findByTenantIdOrderByBenefitTypeAscNameAsc(String tenantId);

    List<BenefitPlan> findByTenantIdAndActiveTrueOrderByBenefitTypeAscNameAsc(String tenantId);

    List<BenefitPlan> findByTenantId(String tenantId);

    List<BenefitPlan> findByTenantIdAndProviderId(String tenantId, UUID providerId);

    /** Used by the enrollment expiry scheduler (M191): plans with a non-null effectiveTo before today. */
    List<BenefitPlan> findByEffectiveToIsNotNullAndEffectiveToBefore(java.time.LocalDate today);
}
