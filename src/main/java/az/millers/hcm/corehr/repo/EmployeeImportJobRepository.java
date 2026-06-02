package az.millers.hcm.corehr.repo;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeImportJob;

public interface EmployeeImportJobRepository
        extends JpaRepository<EmployeeImportJob, UUID> {

    Page<EmployeeImportJob> findAllByOrderByStartedAtDesc(Pageable pageable);
}
