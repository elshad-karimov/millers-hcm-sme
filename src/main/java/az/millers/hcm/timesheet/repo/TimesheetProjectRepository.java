package az.millers.hcm.timesheet.repo;

import az.millers.hcm.timesheet.domain.TimesheetProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M484: Project repository.
 */
@Repository
public interface TimesheetProjectRepository extends JpaRepository<TimesheetProject, UUID> {

    List<TimesheetProject> findByTenantIdOrderByName(String tenantId);

    List<TimesheetProject> findByTenantIdAndActiveOrderByName(String tenantId, Boolean active);

    Optional<TimesheetProject> findByIdAndTenantId(UUID id, String tenantId);

    Optional<TimesheetProject> findByTenantIdAndCode(String tenantId, String code);
}
