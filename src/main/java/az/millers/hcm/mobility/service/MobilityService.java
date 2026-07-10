package az.millers.hcm.mobility.service;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.mobility.domain.InternationalAssignment;
import az.millers.hcm.mobility.repo.InternationalAssignmentRepository;
import az.millers.hcm.security.CurrentRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MobilityService {
    private static final String MODULE = "mobility";
    private static final String TENANT_ID = "default";

    private final InternationalAssignmentRepository repo;
    private final CurrentRequest currentRequest;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public MobilityService(InternationalAssignmentRepository repo, CurrentRequest currentRequest,
                          AuditService audit, NamedParameterJdbcTemplate jdbc) {
        this.repo = repo;
        this.currentRequest = currentRequest;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<InternationalAssignment> listAssignments(String status) {
        return status != null ? repo.findByTenantIdAndStatusOrderByStartDateDesc(TENANT_ID, status)
                              : repo.findByTenantIdOrderByStartDateDesc(TENANT_ID);
    }

    @Transactional(readOnly = true)
    public InternationalAssignment getAssignment(UUID id) {
        return repo.findByIdAndTenantId(id, TENANT_ID)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
    }

    @Transactional(readOnly = true)
    public List<InternationalAssignment> listMyAssignments(UUID employeeId) {
        return repo.findByTenantIdAndEmployeeIdOrderByStartDateDesc(TENANT_ID, employeeId);
    }

    @Transactional
    public InternationalAssignment createAssignment(InternationalAssignment assignment) {
        assignment.setTenantId(TENANT_ID);
        assignment.setAssignmentNo(generateAssignmentNo());
        assignment.setCreatedBy(currentRequest.username());
        InternationalAssignment saved = repo.save(assignment);
        audit.record(MODULE, "InternationalAssignment", saved.getId().toString(), "CREATE", null,
            Map.of("assignmentNo", saved.getAssignmentNo(), "employeeId", saved.getEmployeeId().toString(),
                "hostCountry", saved.getHostCountry()));
        return saved;
    }

    @Transactional
    public InternationalAssignment updateAssignment(UUID id, InternationalAssignment updated) {
        InternationalAssignment existing = getAssignment(id);
        Map<String, Object> old = Map.of("status", existing.getStatus(), "endDate", existing.getEndDate());

        existing.setHostCountry(updated.getHostCountry());
        existing.setHostCity(updated.getHostCity());
        existing.setHostEntity(updated.getHostEntity());
        existing.setPurpose(updated.getPurpose());
        existing.setStartDate(updated.getStartDate());
        existing.setEndDate(updated.getEndDate());
        existing.setStatus(updated.getStatus());
        existing.setVisaType(updated.getVisaType());
        existing.setVisaExpiry(updated.getVisaExpiry());
        existing.setHousingAllowance(updated.getHousingAllowance());
        existing.setColaAmount(updated.getColaAmount());
        existing.setHardshipAmount(updated.getHardshipAmount());
        existing.setNotes(updated.getNotes());
        existing.setUpdatedBy(currentRequest.username());

        InternationalAssignment saved = repo.save(existing);
        audit.record(MODULE, "InternationalAssignment", saved.getId().toString(), "UPDATE", old,
            Map.of("status", saved.getStatus(), "endDate", saved.getEndDate()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InternationalAssignment> expiringVisas(int days) {
        LocalDate expiryDate = LocalDate.now().plusDays(days);
        return repo.findExpiringVisas(TENANT_ID, expiryDate);
    }

    private String generateAssignmentNo() {
        Long seq = jdbc.queryForObject("SELECT nextval('mobility.assignment_seq')",
            new MapSqlParameterSource(), Long.class);
        return String.format("IA-%05d", seq);
    }
}
