package az.millers.hcm.ehs.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.ehs.domain.InspectionFinding;

public interface InspectionFindingRepository extends JpaRepository<InspectionFinding, UUID> {

    List<InspectionFinding> findByInspectionId(UUID inspectionId);

    void deleteByInspectionId(UUID inspectionId);
}
