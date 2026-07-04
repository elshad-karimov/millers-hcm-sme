package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.EmployeePoolMember;

public interface EmployeePoolMemberRepository extends JpaRepository<EmployeePoolMember, UUID> {

    List<EmployeePoolMember> findByPoolIdOrderByAddedAtDesc(UUID poolId);

    List<EmployeePoolMember> findByTenantIdAndEmployeeIdOrderByAddedAtDesc(String tenantId, UUID employeeId);

    boolean existsByPoolIdAndEmployeeId(UUID poolId, UUID employeeId);

    void deleteByPoolIdAndEmployeeId(UUID poolId, UUID employeeId);
}
