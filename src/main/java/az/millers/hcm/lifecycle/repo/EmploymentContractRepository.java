package az.millers.hcm.lifecycle.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.lifecycle.domain.ContractStatus;
import az.millers.hcm.lifecycle.domain.EmploymentContract;

public interface EmploymentContractRepository
        extends JpaRepository<EmploymentContract, UUID> {

    @Query(value = "SELECT nextval('lifecycle.contract_no_seq')", nativeQuery = true)
    long nextContractNoSequence();

    List<EmploymentContract> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);

    Optional<EmploymentContract> findByEmployeeIdAndStatus(UUID employeeId, ContractStatus status);

    boolean existsByContractNo(String contractNo);

    /**
     * Used by {@code EmploymentContractEndDateSource} for the M61 scheduler.
     * Restricted to ACTIVE contracts — terminated/expired/draft ones don't
     * need expiry reminders.
     */
    @Query("""
            select c from EmploymentContract c
            where c.endDate = :date
              and c.status = 'ACTIVE'
            """)
    List<EmploymentContract> findActiveExpiringOn(LocalDate date);

    /**
     * Used by {@code EmploymentContractProbationSource}.
     */
    @Query("""
            select c from EmploymentContract c
            where c.probationEndDate = :date
              and c.status = 'ACTIVE'
            """)
    List<EmploymentContract> findActiveProbationEndingOn(LocalDate date);

    /**
     * ACTIVE contracts for the given employee set whose {@code endDate} falls
     * on or before {@code by}. Used by the manager team dashboard to flag
     * contracts due to expire in the rolling window (M76 / P2-11/12).
     */
    @Query("""
            select c from EmploymentContract c
            where c.employeeId in :employeeIds
              and c.status = 'ACTIVE'
              and c.endDate is not null
              and c.endDate <= :by
            order by c.endDate asc
            """)
    List<EmploymentContract> findActiveExpiringForEmployees(
            java.util.Collection<UUID> employeeIds, LocalDate by);
}
