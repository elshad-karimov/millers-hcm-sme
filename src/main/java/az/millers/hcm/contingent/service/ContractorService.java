package az.millers.hcm.contingent.service;
import az.millers.hcm.common.tenant.TenantContext;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.contingent.domain.ContractorEngagement;
import az.millers.hcm.contingent.repo.ContractorEngagementRepository;
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
public class ContractorService {
    private static final String MODULE = "contingent";

    private final ContractorEngagementRepository repo;
    private final CurrentRequest currentRequest;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public ContractorService(ContractorEngagementRepository repo, CurrentRequest currentRequest,
                            AuditService audit, NamedParameterJdbcTemplate jdbc) {
        this.repo = repo;
        this.currentRequest = currentRequest;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ContractorEngagement> listEngagements(String status) {
        return status != null ? repo.findByTenantIdAndStatusOrderByContractStartDesc(TenantContext.current(), status)
                              : repo.findByTenantIdOrderByContractStartDesc(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public ContractorEngagement getEngagement(UUID id) {
        return repo.findByIdAndTenantId(id, TenantContext.current())
            .orElseThrow(() -> new ResourceNotFoundException("Engagement not found"));
    }

    @Transactional
    public ContractorEngagement createEngagement(ContractorEngagement engagement) {
        engagement.setTenantId(TenantContext.current());
        engagement.setCreatedBy(currentRequest.username());
        ContractorEngagement saved = repo.save(engagement);
        audit.record(MODULE, "ContractorEngagement", saved.getId().toString(), "CREATE", null,
            Map.of("employeeId", saved.getEmployeeId().toString(), "contractStart", saved.getContractStart()));
        return saved;
    }

    @Transactional
    public ContractorEngagement updateEngagement(UUID id, ContractorEngagement updated) {
        ContractorEngagement existing = getEngagement(id);
        Map<String, Object> old = Map.of("status", existing.getStatus(), "contractEnd", existing.getContractEnd());

        existing.setContractStart(updated.getContractStart());
        existing.setContractEnd(updated.getContractEnd());
        existing.setRate(updated.getRate());
        existing.setRateUnit(updated.getRateUnit());
        existing.setPoNumber(updated.getPoNumber());
        existing.setTenureAlertDays(updated.getTenureAlertDays());
        existing.setStatus(updated.getStatus());
        existing.setNotes(updated.getNotes());
        existing.setUpdatedBy(currentRequest.username());

        ContractorEngagement saved = repo.save(existing);
        audit.record(MODULE, "ContractorEngagement", saved.getId().toString(), "UPDATE", old,
            Map.of("status", saved.getStatus(), "contractEnd", saved.getContractEnd()));
        return saved;
    }

    /**
     * M489: Convert contractor to FTE. Updates employee.employment_type, marks engagement CONVERTED,
     * optionally starts onboarding checklist (non-fatal).
     */
    @Transactional
    public void convertToFTE(UUID engagementId, String newEmploymentType, LocalDate effectiveDate) {
        ContractorEngagement engagement = getEngagement(engagementId);
        if (!"ACTIVE".equals(engagement.getStatus())) {
            throw new BadRequestException("Engagement must be ACTIVE to convert");
        }

        UUID employeeId = engagement.getEmployeeId();

        // Update employee.employment_type via direct JDBC (existing employee update path)
        String updateSql = """
            UPDATE core_hr.employee
            SET employment_type = :newType, updated_at = now(), updated_by = :updatedBy
            WHERE id = :employeeId AND tenant_id = :tenantId
            """;

        int updated = jdbc.update(updateSql,
            new MapSqlParameterSource()
                .addValue("newType", newEmploymentType)
                .addValue("updatedBy", currentRequest.username())
                .addValue("employeeId", employeeId)
                .addValue("tenantId", TenantContext.current()));

        if (updated == 0) {
            throw new ResourceNotFoundException("Employee not found");
        }

        // Mark engagement CONVERTED
        engagement.setStatus("CONVERTED");
        engagement.setConversionDate(effectiveDate);
        engagement.setUpdatedBy(currentRequest.username());
        repo.save(engagement);

        audit.record(MODULE, "ContractorConversion", engagement.getId().toString(), "CONVERT",
            Map.of("status", "ACTIVE", "employmentType", "CONTRACTOR"),
            Map.of("status", "CONVERTED", "employmentType", newEmploymentType, "effectiveDate", effectiveDate));

        // Optional: Start onboarding checklist (non-fatal)
        try {
            // ChecklistService.startMatched call would go here (requires dependency injection)
            // Deferred for now (seam documented)
        } catch (Exception e) {
            audit.record(MODULE, "ContractorConversion", engagement.getId().toString(), "ONBOARDING_ERROR", null,
                Map.of("error", e.getMessage()));
        }
    }
}
