package az.millers.hcm.payroll.timepay;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeExcessRuleRepository extends JpaRepository<EmployeeExcessRule, UUID> {

    List<EmployeeExcessRule> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);
}
