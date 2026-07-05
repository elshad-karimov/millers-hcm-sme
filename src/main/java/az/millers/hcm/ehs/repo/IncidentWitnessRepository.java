package az.millers.hcm.ehs.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.ehs.domain.IncidentWitness;

public interface IncidentWitnessRepository extends JpaRepository<IncidentWitness, UUID> {

    List<IncidentWitness> findByIncidentId(UUID incidentId);

    void deleteByIncidentId(UUID incidentId);
}
