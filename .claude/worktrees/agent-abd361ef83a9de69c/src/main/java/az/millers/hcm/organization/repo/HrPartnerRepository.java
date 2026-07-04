package az.millers.hcm.organization.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.organization.domain.HrPartner;

public interface HrPartnerRepository extends JpaRepository<HrPartner, UUID> {
    List<HrPartner> findByOrgUnitIdAndActiveTrueOrderByBackupAscCreatedAtAsc(UUID orgUnitId);
    List<HrPartner> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
    boolean existsByOrgUnitIdAndEmployeeIdAndEffectiveFrom(UUID orgUnitId, UUID employeeId,
            java.time.LocalDate effectiveFrom);
}
