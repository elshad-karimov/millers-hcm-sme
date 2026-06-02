package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeNote;

public interface EmployeeNoteRepository
        extends JpaRepository<EmployeeNote, UUID> {

    /**
     * All notes for the employee, pinned first, then newest. The service
     * applies the per-row visibility filter on the way out (cannot push the
     * role check into SQL).
     */
    List<EmployeeNote> findByEmployeeIdOrderByPinnedDescCreatedAtDesc(UUID employeeId);
}
