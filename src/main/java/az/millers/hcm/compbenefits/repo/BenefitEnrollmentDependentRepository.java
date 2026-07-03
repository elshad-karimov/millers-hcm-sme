package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.BenefitEnrollmentDependent;

public interface BenefitEnrollmentDependentRepository
        extends JpaRepository<BenefitEnrollmentDependent, UUID> {

    List<BenefitEnrollmentDependent> findByEnrollmentId(UUID enrollmentId);

    List<BenefitEnrollmentDependent> findByEnrollmentIdIn(List<UUID> enrollmentIds);
}
