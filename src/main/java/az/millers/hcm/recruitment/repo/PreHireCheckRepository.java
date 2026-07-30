package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.PreHireCheck;

public interface PreHireCheckRepository extends JpaRepository<PreHireCheck, UUID> {

    @org.springframework.data.jpa.repository.Query(
            value = "SELECT config.next_tenant_seq('recruitment.check_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<PreHireCheck> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    /**
     * M286 — hire-gate query: checks on an application that block the
     * hire and have FAILED. A non-empty result stops the hire.
     */
    @org.springframework.data.jpa.repository.Query("""
            select c from PreHireCheck c
            where c.applicationId = :applicationId
              and c.blocksHire = true
              and (c.status = az.millers.hcm.recruitment.domain.PreHireCheck.Status.FAILED
                   or c.result = az.millers.hcm.recruitment.domain.PreHireCheck.Result.FAIL)
            """)
    List<PreHireCheck> findBlockingFailures(
            @org.springframework.data.repository.query.Param("applicationId") UUID applicationId);
}
