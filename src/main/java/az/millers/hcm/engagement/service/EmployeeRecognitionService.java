package az.millers.hcm.engagement.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.engagement.domain.EmployeeRecognition;
import az.millers.hcm.engagement.domain.RecognitionStatus;
import az.millers.hcm.engagement.domain.RecognitionVisibility;
import az.millers.hcm.engagement.repo.EmployeeRecognitionRepository;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M478 — Recognition/kudos service.
 * Peer-to-peer (any employee sends; not to self).
 */
@Service
public class EmployeeRecognitionService {

    private static final String TENANT = "default";
    private static final String MODULE = "engagement";
    private static final String ENTITY = "Recognition";

    private final EmployeeRecognitionRepository repository;
    private final EmployeeContextService employeeContext;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public EmployeeRecognitionService(EmployeeRecognitionRepository repository,
                                     EmployeeContextService employeeContext,
                                     AuditService audit,
                                     NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.employeeContext = employeeContext;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    /**
     * Public wall: recent PUBLIC+ACTIVE recognitions with employee names.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> publicWall(int limit) {
        List<EmployeeRecognition> recognitions = repository.findPublicWall(
                TENANT, RecognitionStatus.ACTIVE, RecognitionVisibility.PUBLIC,
                PageRequest.of(0, limit));

        // Enrich with employee names via JDBC projection (tenant-filtered)
        List<Map<String, Object>> result = new ArrayList<>();
        for (EmployeeRecognition r : recognitions) {
            Map<String, Object> fromEmployee = jdbc.queryForMap(
                    "SELECT first_name, last_name FROM core_hr.employee WHERE id = :id AND tenant_id = :tenant",
                    new MapSqlParameterSource()
                            .addValue("id", r.getFromEmployeeId())
                            .addValue("tenant", TENANT));

            Map<String, Object> toEmployee = jdbc.queryForMap(
                    "SELECT first_name, last_name FROM core_hr.employee WHERE id = :id AND tenant_id = :tenant",
                    new MapSqlParameterSource()
                            .addValue("id", r.getToEmployeeId())
                            .addValue("tenant", TENANT));

            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("fromEmployee", fromEmployee.get("first_name") + " " + fromEmployee.get("last_name"));
            item.put("toEmployee", toEmployee.get("first_name") + " " + toEmployee.get("last_name"));
            item.put("valueTag", r.getValueTag());
            item.put("message", r.getMessage());
            item.put("createdAt", r.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    /**
     * My sent recognitions.
     */
    @Transactional(readOnly = true)
    public List<EmployeeRecognition> mySent() {
        UUID currentEmployeeId = employeeContext.currentEmployee().getId();
        return repository.findByTenantIdAndFromEmployeeIdOrderByCreatedAtDesc(TENANT, currentEmployeeId);
    }

    /**
     * My received recognitions.
     */
    @Transactional(readOnly = true)
    public List<EmployeeRecognition> myReceived() {
        UUID currentEmployeeId = employeeContext.currentEmployee().getId();
        return repository.findByTenantIdAndToEmployeeIdOrderByCreatedAtDesc(TENANT, currentEmployeeId);
    }

    /**
     * Send recognition (from = current employee, never spoofable).
     */
    @Transactional
    public EmployeeRecognition send(UUID toEmployeeId, EmployeeRecognition recognition) {
        UUID fromEmployeeId = employeeContext.currentEmployee().getId();

        if (fromEmployeeId.equals(toEmployeeId)) {
            throw new BadRequestException("Cannot recognize yourself");
        }

        recognition.setTenantId(TENANT);
        recognition.setFromEmployeeId(fromEmployeeId);
        recognition.setToEmployeeId(toEmployeeId);
        recognition.setStatus(RecognitionStatus.ACTIVE);

        EmployeeRecognition saved = repository.save(recognition);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "SEND",
                null, Map.of("from", fromEmployeeId, "to", toEmployeeId, "valueTag", recognition.getValueTag()));

        return saved;
    }

    /**
     * HR hide/unhide (audited).
     */
    @Transactional
    public EmployeeRecognition updateStatus(UUID id, RecognitionStatus status) {
        EmployeeRecognition recognition = repository.findById(id)
                .filter(r -> TENANT.equals(r.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Recognition not found"));

        RecognitionStatus oldStatus = recognition.getStatus();
        recognition.setStatus(status);
        EmployeeRecognition saved = repository.save(recognition);

        audit.record(MODULE, ENTITY, id.toString(), "STATUS_CHANGE",
                Map.of("status", oldStatus), Map.of("status", status));

        return saved;
    }
}
