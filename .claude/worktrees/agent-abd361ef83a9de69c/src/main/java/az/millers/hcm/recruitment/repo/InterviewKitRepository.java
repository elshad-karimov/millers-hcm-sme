package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.InterviewKit;

public interface InterviewKitRepository extends JpaRepository<InterviewKit, UUID> {
    Optional<InterviewKit> findByCode(String code);
    List<InterviewKit> findByActiveTrueOrderByNameAsc();
    List<InterviewKit> findByJobFamilyIdAndActiveTrueOrderByNameAsc(UUID jobFamilyId);
}
