package az.millers.hcm.preboarding.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.preboarding.domain.PreboardingInvite;
import az.millers.hcm.preboarding.domain.PreboardingStatus;

public interface PreboardingInviteRepository extends JpaRepository<PreboardingInvite, UUID> {

    /** Hot-path lookup from the public REST handler. */
    Optional<PreboardingInvite> findByTokenHash(String tokenHash);

    List<PreboardingInvite> findAllByOrderByCreatedAtDesc();

    List<PreboardingInvite> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<PreboardingInvite> findByStatusInOrderByExpiresAtAsc(List<PreboardingStatus> statuses);

    /** Used by the expiry scheduler (M190): SENT/OPENED invites past their expiry timestamp. */
    List<PreboardingInvite> findByStatusInAndExpiresAtBefore(
            List<PreboardingStatus> statuses, java.time.OffsetDateTime cutoff);
}
