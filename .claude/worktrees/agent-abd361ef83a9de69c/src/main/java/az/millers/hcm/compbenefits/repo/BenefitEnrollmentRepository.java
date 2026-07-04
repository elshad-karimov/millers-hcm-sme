package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.BenefitEnrollment;
import az.millers.hcm.compbenefits.domain.EnrollmentStatus;

public interface BenefitEnrollmentRepository extends JpaRepository<BenefitEnrollment, UUID> {

    List<BenefitEnrollment> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);

    List<BenefitEnrollment> findByPlanIdOrderByStartDateDesc(UUID planId);

    List<BenefitEnrollment> findByStatusOrderByStartDateDesc(EnrollmentStatus status);

    long countByPlanIdAndStatus(UUID planId, EnrollmentStatus status);

    boolean existsByEmployeeIdAndPlanIdAndStatus(UUID employeeId, UUID planId, EnrollmentStatus status);

    /** Used by the enrollment expiry scheduler (M191): active enrollments for a given plan. */
    List<BenefitEnrollment> findByPlanIdAndStatus(UUID planId, EnrollmentStatus status);
}
