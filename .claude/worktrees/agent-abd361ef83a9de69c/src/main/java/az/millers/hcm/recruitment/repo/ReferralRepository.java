package az.millers.hcm.recruitment.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.Referral;
import az.millers.hcm.recruitment.domain.ReferralStatus;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    @Query(value = "SELECT nextval('recruitment.referral_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<Referral> findAllByOrderByCreatedAtDesc();

    List<Referral> findByStatusOrderByCreatedAtDesc(ReferralStatus status);

    List<Referral> findByReferrerEmployeeIdOrderByCreatedAtDesc(UUID referrerEmployeeId);

    Optional<Referral> findByCandidateId(UUID candidateId);

    boolean existsByCandidateId(UUID candidateId);

    /** HIRED referrals whose qualifying clock has elapsed — promote to QUALIFIED. */
    List<Referral> findByStatusAndQualifiedAtLessThanEqual(ReferralStatus status, OffsetDateTime asOf);
}
