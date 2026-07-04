package az.millers.hcm.leave.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.leave.domain.LeaveType;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    Optional<LeaveType> findByCode(String code);

    boolean existsByCode(String code);

    List<LeaveType> findAllByOrderByNameAsc();

    List<LeaveType> findByActiveTrueOrderByNameAsc();

    /**
     * Types eligible for the monthly accrual walker (PRD 8.5.2 —
     * milestone 34). Used by {@code LeaveAccrualService}.
     */
    List<LeaveType> findByActiveTrueAndAccruesMonthlyTrueOrderByCodeAsc();
}
