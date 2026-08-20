package az.millers.hcm.payroll.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CalculationProfileRepository extends JpaRepository<CalculationProfile, UUID> {

    Optional<CalculationProfile> findByCode(String code);

    List<CalculationProfile> findByActiveTrueOrderByCodeAsc();
}
