package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.payroll.domain.PayrollGroup;

public interface PayrollGroupRepository extends JpaRepository<PayrollGroup, UUID> {
    Optional<PayrollGroup> findByCode(String code);
    Optional<PayrollGroup> findFirstByDefaultGroupTrue();
    List<PayrollGroup> findByActiveTrueOrderByNameAsc();
}
