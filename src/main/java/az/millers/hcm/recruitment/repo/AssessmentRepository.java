package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.recruitment.domain.Assessment;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('recruitment.assessment_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<Assessment> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    /** M287 — hire-gate query: blocks-hire assessments that FAILED. */
    @Query("""
            select a from Assessment a
            where a.applicationId = :applicationId
              and a.blocksHire = true
              and a.result = az.millers.hcm.recruitment.domain.Assessment.Result.FAIL
            """)
    List<Assessment> findBlockingFailures(@Param("applicationId") UUID applicationId);
}
