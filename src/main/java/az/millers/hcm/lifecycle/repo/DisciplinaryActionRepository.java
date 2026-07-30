package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.lifecycle.domain.DisciplinaryAction;
import az.millers.hcm.lifecycle.domain.DisciplinaryStatus;

public interface DisciplinaryActionRepository
        extends JpaRepository<DisciplinaryAction, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('lifecycle.disciplinary_no_seq')", nativeQuery = true)
    long nextActionNoSequence();

    List<DisciplinaryAction> findByEmployeeIdOrderByActionDateDesc(UUID employeeId);

    Page<DisciplinaryAction> findByStatusOrderByActionDateDesc(DisciplinaryStatus status, Pageable pageable);

    Page<DisciplinaryAction> findAllByOrderByActionDateDesc(Pageable pageable);
}
