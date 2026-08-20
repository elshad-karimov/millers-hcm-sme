package az.millers.hcm.payroll.timepay;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimePayRuleOverrideRepository extends JpaRepository<TimePayRuleOverride, UUID> {

    List<TimePayRuleOverride> findByEmployeeId(UUID employeeId);
}
