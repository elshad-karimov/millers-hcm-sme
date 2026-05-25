package az.millers.hcm.career.repo;

import az.millers.hcm.career.domain.Idp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdpRepository extends JpaRepository<Idp, UUID> {

    List<Idp> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    Optional<Idp> findByEmployeeIdAndStatus(UUID employeeId, String status);
}
