package az.millers.hcm.timesheet.service;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.timesheet.domain.TimesheetProject;
import az.millers.hcm.timesheet.repo.TimesheetProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M484: Project service for timesheet billing/costing.
 */
@Service
public class TimesheetProjectService {

    private static final String MODULE = "timesheet";
    private static final String TENANT_ID = "default";

    private final TimesheetProjectRepository projectRepo;
    private final CurrentRequest currentRequest;
    private final AuditService audit;

    public TimesheetProjectService(TimesheetProjectRepository projectRepo,
                                  CurrentRequest currentRequest,
                                  AuditService audit) {
        this.projectRepo = projectRepo;
        this.currentRequest = currentRequest;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TimesheetProject> listProjects(boolean activeOnly) {
        return activeOnly
            ? projectRepo.findByTenantIdAndActiveOrderByName(TENANT_ID, true)
            : projectRepo.findByTenantIdOrderByName(TENANT_ID);
    }

    @Transactional(readOnly = true)
    public TimesheetProject getProject(UUID id) {
        return projectRepo.findByIdAndTenantId(id, TENANT_ID)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    @Transactional
    public TimesheetProject createProject(TimesheetProject project) {
        project.setTenantId(TENANT_ID);
        project.setCreatedBy(currentRequest.username());

        TimesheetProject saved = projectRepo.save(project);
        audit.record(MODULE, "TimesheetProject", saved.getId().toString(), "CREATE", null,
            Map.of("code", saved.getCode(), "name", saved.getName()));
        return saved;
    }

    @Transactional
    public TimesheetProject updateProject(UUID id, TimesheetProject updated) {
        TimesheetProject existing = getProject(id);
        Map<String, Object> old = Map.of("name", existing.getName(), "billingRate", existing.getBillingRate(),
            "active", existing.getActive());

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setBillingRate(updated.getBillingRate());
        existing.setActive(updated.getActive());
        existing.setUpdatedBy(currentRequest.username());

        TimesheetProject saved = projectRepo.save(existing);
        audit.record(MODULE, "TimesheetProject", saved.getId().toString(), "UPDATE", old,
            Map.of("name", saved.getName(), "billingRate", saved.getBillingRate(),
                "active", saved.getActive()));
        return saved;
    }

    /**
     * Validate project is active (called by timesheet services before assignment).
     */
    public void validateProject(UUID projectId) {
        if (projectId == null) return;
        TimesheetProject project = getProject(projectId);
        if (!project.getActive()) {
            throw new BadRequestException("Project is not active");
        }
    }
}
