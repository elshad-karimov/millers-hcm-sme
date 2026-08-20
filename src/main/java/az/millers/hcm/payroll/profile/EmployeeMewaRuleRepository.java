package az.millers.hcm.payroll.profile;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeMewaRuleRepository extends JpaRepository<EmployeeMewaRule, UUID> {

    List<EmployeeMewaRule> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);
}
