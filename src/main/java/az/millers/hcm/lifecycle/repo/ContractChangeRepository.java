package az.millers.hcm.lifecycle.repo;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.lifecycle.domain.ChangeType;
import az.millers.hcm.lifecycle.domain.ContractChange;
import az.millers.hcm.lifecycle.domain.ContractChangeStatus;

public interface ContractChangeRepository extends JpaRepository<ContractChange, UUID> {

    @Query(value = "SELECT nextval('lifecycle.contract_change_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Page<ContractChange> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ContractChange> findByStatusOrderByCreatedAtDesc(ContractChangeStatus status, Pageable pageable);

    Page<ContractChange> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId, Pageable pageable);

    Page<ContractChange> findByChangeTypeOrderByCreatedAtDesc(ChangeType changeType, Pageable pageable);
}
