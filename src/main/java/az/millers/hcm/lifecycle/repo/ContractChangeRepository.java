package az.millers.hcm.lifecycle.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.lifecycle.domain.ChangeType;
import az.millers.hcm.lifecycle.domain.ContractChange;
import az.millers.hcm.lifecycle.domain.ContractChangeStatus;

public interface ContractChangeRepository extends JpaRepository<ContractChange, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('lifecycle.contract_change_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Page<ContractChange> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ContractChange> findByStatusOrderByCreatedAtDesc(ContractChangeStatus status, Pageable pageable);

    Page<ContractChange> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId, Pageable pageable);

    Page<ContractChange> findByChangeTypeOrderByCreatedAtDesc(ChangeType changeType, Pageable pageable);

    /**
     * Returns APPROVED changes whose effective date is on or before {@code asOf}
     * — used by the daily activation scheduler (PRD §8.12.6).
     */
    List<ContractChange> findByStatusAndEffectiveDateLessThanEqual(
            ContractChangeStatus status, LocalDate asOf);
}
