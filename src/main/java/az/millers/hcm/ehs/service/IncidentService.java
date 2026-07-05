package az.millers.hcm.ehs.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.ehs.domain.*;
import az.millers.hcm.ehs.repo.IncidentInvolvedRepository;
import az.millers.hcm.ehs.repo.IncidentRepository;
import az.millers.hcm.ehs.repo.IncidentWitnessRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M448 — EHS incident management.
 * Any authenticated employee can report an incident.
 * CRITICAL/SERIOUS incidents auto-trigger investigation_required and notify the reporter's manager (simplified HR notification).
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
    private static final String TENANT = "default";
    private static final String MODULE = "ehs";
    private static final String ENTITY = "Incident";

    private final IncidentRepository repo;
    private final IncidentInvolvedRepository involvedRepo;
    private final IncidentWitnessRepository witnessRepo;
    private final EmployeeRepository employeeRepo;
    private final EmployeeContextService employeeContext;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final NotificationService notificationService;
    private final NamedParameterJdbcTemplate jdbc;

    // Cache employee context to avoid repeated lookups in get() filtering
    private UUID cachedCurrentEmployeeId;

    public IncidentService(IncidentRepository repo,
                           IncidentInvolvedRepository involvedRepo,
                           IncidentWitnessRepository witnessRepo,
                           EmployeeRepository employeeRepo,
                           EmployeeContextService employeeContext,
                           AuditService audit,
                           CurrentRequest currentRequest,
                           NotificationService notificationService,
                           NamedParameterJdbcTemplate jdbc) {
        this.repo = repo;
        this.involvedRepo = involvedRepo;
        this.witnessRepo = witnessRepo;
        this.employeeRepo = employeeRepo;
        this.employeeContext = employeeContext;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.notificationService = notificationService;
        this.jdbc = jdbc;
    }

    @Transactional
    public Incident report(LocalDate incidentDate,
                           LocalTime incidentTime,
                           UUID workLocationId,
                           UUID orgUnitId,
                           IncidentType incidentType,
                           IncidentSeverity severity,
                           String description,
                           String immediateAction,
                           List<UUID> involvedEmployeeIds,
                           List<WitnessInput> witnesses) {

        // Reporter = current employee
        Employee reporter = employeeContext.currentEmployee();

        // Generate incident number
        String incidentNo = generateIncidentNo();

        Incident incident = new Incident();
        incident.setTenantId(TENANT);
        incident.setIncidentNo(incidentNo);
        incident.setIncidentDate(incidentDate);
        incident.setIncidentTime(incidentTime);
        incident.setWorkLocationId(workLocationId);
        incident.setOrgUnitId(orgUnitId);
        incident.setIncidentType(incidentType);
        incident.setSeverity(severity);
        incident.setReportedByEmployeeId(reporter.getId());
        incident.setDescription(description);
        incident.setImmediateAction(immediateAction);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setCreatedBy(currentRequest.username());
        incident.setUpdatedBy(currentRequest.username());

        // Auto-set investigation_required for CRITICAL/SERIOUS
        if (severity == IncidentSeverity.CRITICAL || severity == IncidentSeverity.SERIOUS) {
            incident.setInvestigationRequired(true);
        } else {
            incident.setInvestigationRequired(false);
        }

        Incident saved = repo.save(incident);

        // Save involved employees
        if (involvedEmployeeIds != null) {
            for (UUID empId : involvedEmployeeIds) {
                IncidentInvolved inv = new IncidentInvolved();
                inv.setIncidentId(saved.getId());
                inv.setEmployeeId(empId);
                involvedRepo.save(inv);
            }
        }

        // Save witnesses
        if (witnesses != null) {
            for (WitnessInput wit : witnesses) {
                IncidentWitness w = new IncidentWitness();
                w.setIncidentId(saved.getId());
                w.setName(wit.name());
                w.setContact(wit.contact());
                w.setStatement(wit.statement());
                witnessRepo.save(w);
            }
        }

        audit.record(MODULE, ENTITY, saved.getId().toString(), "REPORTED", null, null);

        // Notify for CRITICAL/SERIOUS incidents — notify reporter's manager (or reporter if no manager)
        if (incident.getInvestigationRequired()) {
            notifyForCriticalIncident(saved, reporter);
        }

        return saved;
    }

    @Transactional
    public Incident update(UUID id,
                           String description,
                           String immediateAction,
                           IncidentStatus status,
                           List<UUID> involvedEmployeeIds,
                           List<WitnessInput> witnesses) {

        Incident incident = get(id);

        IncidentStatus oldStatus = incident.getStatus();

        if (description != null) {
            incident.setDescription(description);
        }
        if (immediateAction != null) {
            incident.setImmediateAction(immediateAction);
        }
        if (status != null) {
            incident.setStatus(status);
        }

        incident.setUpdatedBy(currentRequest.username());
        incident.setUpdatedAt(OffsetDateTime.now());

        Incident updated = repo.save(incident);

        // Update involved employees if provided
        if (involvedEmployeeIds != null) {
            involvedRepo.deleteByIncidentId(id);
            for (UUID empId : involvedEmployeeIds) {
                IncidentInvolved inv = new IncidentInvolved();
                inv.setIncidentId(id);
                inv.setEmployeeId(empId);
                involvedRepo.save(inv);
            }
        }

        // Update witnesses if provided
        if (witnesses != null) {
            witnessRepo.deleteByIncidentId(id);
            for (WitnessInput wit : witnesses) {
                IncidentWitness w = new IncidentWitness();
                w.setIncidentId(id);
                w.setName(wit.name());
                w.setContact(wit.contact());
                w.setStatement(wit.statement());
                witnessRepo.save(w);
            }
        }

        if (status != null && !status.equals(oldStatus)) {
            audit.record(MODULE, ENTITY, id.toString(), "STATUS_CHANGE",
                    Map.of("status", oldStatus),
                    Map.of("status", status));
        }

        return updated;
    }

    @Transactional
    public void close(UUID id, String resolution) {
        Incident incident = get(id);

        // CLOSED requires investigation done when investigation_required
        if (incident.getInvestigationRequired() && incident.getStatus() == IncidentStatus.UNDER_INVESTIGATION) {
            if (resolution == null || resolution.isBlank()) {
                throw new IllegalArgumentException("Resolution text required for incidents under investigation");
            }
        }

        incident.setStatus(IncidentStatus.CLOSED);
        incident.setClosedAt(OffsetDateTime.now());
        incident.setUpdatedBy(currentRequest.username());
        incident.setUpdatedAt(OffsetDateTime.now());

        if (resolution != null) {
            incident.setImmediateAction(resolution); // Store resolution in immediate_action field
        }

        repo.save(incident);

        audit.record(MODULE, ENTITY, id.toString(), "CLOSED", null, null);
    }

    @Transactional(readOnly = true)
    public Incident get(UUID id) {
        Incident incident = repo.findByIdAndTenantId(id, TENANT)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));

        // Tenant post-check
        if (!TENANT.equals(incident.getTenantId())) {
            throw new ResourceNotFoundException("Incident not found: " + id);
        }

        // IDOR guard: non-HR users can only see incidents they reported
        boolean isHR = currentRequest.hasRole("HR_ADMIN")
                    || currentRequest.hasRole("HR_SPECIALIST")
                    || currentRequest.hasRole("SYSTEM_ADMIN");

        if (!isHR) {
            if (cachedCurrentEmployeeId == null) {
                try {
                    cachedCurrentEmployeeId = employeeContext.currentEmployee().getId();
                } catch (Exception e) {
                    throw new ResourceNotFoundException("Incident not found: " + id);
                }
            }
            if (!cachedCurrentEmployeeId.equals(incident.getReportedByEmployeeId())) {
                throw new ResourceNotFoundException("Incident not found: " + id);
            }
        }

        return incident;
    }

    @Transactional(readOnly = true)
    public List<Incident> list(IncidentStatus statusFilter, UUID reportedByEmployeeId) {
        // Server-side scope: reporter sees own reports, HR_ADMIN/HR_SPECIALIST see all
        boolean isHR = currentRequest.hasRole("HR_ADMIN") || currentRequest.hasRole("HR_SPECIALIST");

        if (statusFilter != null && reportedByEmployeeId != null) {
            if (isHR) {
                return repo.findByTenantIdAndReportedByEmployeeIdOrderByIncidentDateDesc(TENANT, reportedByEmployeeId)
                        .stream().filter(i -> i.getStatus() == statusFilter).toList();
            } else {
                UUID currentEmpId = employeeContext.currentEmployee().getId();
                if (!currentEmpId.equals(reportedByEmployeeId)) {
                    return List.of(); // Can only see own reports
                }
                return repo.findByTenantIdAndReportedByEmployeeIdOrderByIncidentDateDesc(TENANT, reportedByEmployeeId)
                        .stream().filter(i -> i.getStatus() == statusFilter).toList();
            }
        } else if (statusFilter != null) {
            if (isHR) {
                return repo.findByTenantIdAndStatusOrderByIncidentDateDesc(TENANT, statusFilter);
            } else {
                UUID currentEmpId = employeeContext.currentEmployee().getId();
                return repo.findByTenantIdAndReportedByEmployeeIdOrderByIncidentDateDesc(TENANT, currentEmpId)
                        .stream().filter(i -> i.getStatus() == statusFilter).toList();
            }
        } else if (reportedByEmployeeId != null) {
            if (isHR) {
                return repo.findByTenantIdAndReportedByEmployeeIdOrderByIncidentDateDesc(TENANT, reportedByEmployeeId);
            } else {
                UUID currentEmpId = employeeContext.currentEmployee().getId();
                if (!currentEmpId.equals(reportedByEmployeeId)) {
                    return List.of();
                }
                return repo.findByTenantIdAndReportedByEmployeeIdOrderByIncidentDateDesc(TENANT, reportedByEmployeeId);
            }
        } else {
            if (isHR) {
                return repo.findByTenantIdOrderByIncidentDateDesc(TENANT);
            } else {
                UUID currentEmpId = employeeContext.currentEmployee().getId();
                return repo.findByTenantIdAndReportedByEmployeeIdOrderByIncidentDateDesc(TENANT, currentEmpId);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<IncidentInvolved> getInvolved(UUID incidentId) {
        // Verify incident exists and tenant matches
        get(incidentId);
        return involvedRepo.findByIncidentId(incidentId);
    }

    @Transactional(readOnly = true)
    public List<IncidentWitness> getWitnesses(UUID incidentId) {
        // Verify incident exists and tenant matches
        get(incidentId);
        return witnessRepo.findByIncidentId(incidentId);
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private String generateIncidentNo() {
        Long seq = jdbc.queryForObject(
                "SELECT nextval('ehs.incident_no_seq')",
                Map.of(),
                Long.class
        );
        return String.format("INC-%05d", seq);
    }

    private void notifyForCriticalIncident(Incident incident, Employee reporter) {
        try {
            // Notify reporter's manager if exists, otherwise notify reporter
            UUID recipientId = reporter.getManagerId() != null ? reporter.getManagerId() : reporter.getId();
            Employee recipient = employeeRepo.findById(recipientId).orElse(reporter);

            if (recipient.getUsername() != null) {
                String title = "Critical Incident Reported: " + incident.getIncidentNo();
                String body = String.format("A %s severity %s incident was reported on %s. Investigation required.",
                        incident.getSeverity(), incident.getIncidentType(), incident.getIncidentDate());

                notificationService.notifyAll(
                        NotificationCategory.TRANSACTIONAL,
                        recipient.getUsername(),
                        title,
                        body,
                        MODULE,
                        ENTITY,
                        incident.getId().toString()
                );
            }
        } catch (Exception e) {
            // Non-fatal: log and continue
            log.warn("Failed to send critical incident notification for {}: {}", incident.getIncidentNo(), e.getMessage());
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record WitnessInput(String name, String contact, String statement) {}
}
