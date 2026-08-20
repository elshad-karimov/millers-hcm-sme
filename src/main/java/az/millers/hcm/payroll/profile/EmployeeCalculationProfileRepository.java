package az.millers.hcm.payroll.profile;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeCalculationProfileRepository
        extends JpaRepository<EmployeeCalculationProfile, UUID> {

    List<EmployeeCalculationProfile> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);
}
