package az.millers.hcm.ehs.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.ehs.domain.IncidentInvolved;

public interface IncidentInvolvedRepository extends JpaRepository<IncidentInvolved, UUID> {

    List<IncidentInvolved> findByIncidentId(UUID incidentId);

    void deleteByIncidentId(UUID incidentId);
}
