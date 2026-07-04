package az.millers.hcm.organization.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.organization.domain.Location;

public interface LocationRepository extends JpaRepository<Location, UUID> {
    boolean existsByCode(String code);
    List<Location> findAllByOrderByNameAsc();
    List<Location> findByActiveTrueOrderByNameAsc();
}
