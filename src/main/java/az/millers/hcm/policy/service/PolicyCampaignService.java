package az.millers.hcm.policy.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.policy.domain.AcknowledgementCampaign;
import az.millers.hcm.policy.domain.CampaignAudience;
import az.millers.hcm.policy.domain.CampaignStatus;
import az.millers.hcm.policy.repo.AcknowledgementCampaignRepository;
import az.millers.hcm.policy.repo.PolicyAcknowledgementRepository;
import az.millers.hcm.policy.repo.PolicyDocumentRepository;

/**
 * M490 — Policy re-acknowledgement campaign orchestration. HR launches
 * campaigns to notify employees to re-ack a specific policy version.
 * Progress tracked by audience.
 */
@Service
@Transactional
public class PolicyCampaignService {

    private static final Logger log = LoggerFactory.getLogger(PolicyCampaignService.class);

    private final AcknowledgementCampaignRepository campaignRepo;
    private final PolicyDocumentRepository policyRepo;
    private final PolicyAcknowledgementRepository ackRepo;
    private final NotificationService notifications;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;
    private final az.millers.hcm.security.CurrentRequest currentRequest;

    public PolicyCampaignService(AcknowledgementCampaignRepository campaignRepo,
                                  PolicyDocumentRepository policyRepo,
                                  PolicyAcknowledgementRepository ackRepo,
                                  NotificationService notifications,
                                  AuditService audit,
                                  NamedParameterJdbcTemplate jdbc,
                                  az.millers.hcm.security.CurrentRequest currentRequest) {
        this.campaignRepo = campaignRepo;
        this.policyRepo = policyRepo;
        this.ackRepo = ackRepo;
        this.notifications = notifications;
        this.audit = audit;
        this.jdbc = jdbc;
        this.currentRequest = currentRequest;
    }

    /**
     * Create a new campaign in DRAFT status.
     */
    public AcknowledgementCampaign create(UUID policyId, int policyVersion, String name,
                                           CampaignAudience audience, UUID audienceRef,
                                           LocalDate dueDate, String createdBy) {
        String tenantId = TenantContext.current();

        // Verify policy exists
        policyRepo.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        AcknowledgementCampaign campaign = new AcknowledgementCampaign();
        campaign.setTenantId(tenantId);
        campaign.setPolicyId(policyId);
        campaign.setPolicyVersion(policyVersion);
        campaign.setName(name);
        campaign.setAudience(audience);
        campaign.setAudienceRef(audienceRef);
        campaign.setDueDate(dueDate != null ? dueDate : LocalDate.now().plusDays(14));
        campaign.setCreatedBy(createdBy);

        AcknowledgementCampaign saved = campaignRepo.save(campaign);

        audit.record("POLICY_CAMPAIGN", "AcknowledgementCampaign", saved.getId().toString(),
                    "CREATED", null, saved);

        log.info("Created policy campaign {} for policy {} v{}", saved.getId(), policyId, policyVersion);
        return saved;
    }

    /**
     * Launch campaign → notify all audience employees.
     */
    public void launch(UUID campaignId, String launchedBy) {
        String tenantId = TenantContext.current();
        AcknowledgementCampaign campaign = campaignRepo.findByIdAndTenantId(campaignId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new IllegalStateException("Campaign already launched: " + campaignId);
        }

        // Fetch audience employees
        List<Map<String, Object>> audienceEmployees = fetchAudienceEmployees(campaign);

        // Notify each employee (batched)
        String policyTitle = fetchPolicyTitle(campaign.getPolicyId());
        String messageTitle = "Policy Acknowledgement Required";
        String messageBody = String.format(
            "Please review and acknowledge the policy: %s (version %d). Due date: %s",
            policyTitle, campaign.getPolicyVersion(), campaign.getDueDate()
        );

        for (Map<String, Object> emp : audienceEmployees) {
            String username = (String) emp.get("username");
            try {
                notifications.createInApp(username, messageTitle, messageBody,
                    "policy", "campaign", campaignId.toString());
            } catch (Exception ex) {
                log.warn("Failed to notify {} for campaign {}: {}", username, campaignId, ex.getMessage());
            }
        }

        // Update campaign status
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setLaunchedAt(OffsetDateTime.now());
        campaign.setLaunchedBy(launchedBy);
        campaign.setUpdatedBy(launchedBy);
        AcknowledgementCampaign updated = campaignRepo.save(campaign);

        audit.record("POLICY_CAMPAIGN", "AcknowledgementCampaign", campaignId.toString(),
                    "LAUNCHED", CampaignStatus.DRAFT, CampaignStatus.ACTIVE);

        log.info("Launched campaign {} — notified {} employees", campaignId, audienceEmployees.size());
    }

    /**
     * Close an ACTIVE campaign.
     */
    public void close(UUID campaignId, String closedBy) {
        String tenantId = TenantContext.current();
        AcknowledgementCampaign campaign = campaignRepo.findByIdAndTenantId(campaignId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new IllegalStateException("Campaign not active: " + campaignId);
        }

        campaign.setStatus(CampaignStatus.CLOSED);
        campaign.setUpdatedBy(closedBy);
        AcknowledgementCampaign closed = campaignRepo.save(campaign);

        audit.record("POLICY_CAMPAIGN", "AcknowledgementCampaign", campaignId.toString(),
                    "CLOSED", CampaignStatus.ACTIVE, CampaignStatus.CLOSED);
        log.info("Closed campaign {}", campaignId);
    }

    /**
     * Get campaign progress: acked count vs total audience, plus per-department breakdown.
     */
    public CampaignProgress getProgress(UUID campaignId) {
        String tenantId = TenantContext.current();
        AcknowledgementCampaign campaign = campaignRepo.findByIdAndTenantId(campaignId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        List<Map<String, Object>> audienceEmployees = fetchAudienceEmployees(campaign);
        int totalAudience = audienceEmployees.size();

        // Count how many have acknowledged this policy version
        int ackedCount = 0;
        if (totalAudience > 0) {
            List<UUID> audienceIds = audienceEmployees.stream()
                .map(e -> (UUID) e.get("id"))
                .collect(Collectors.toList());

            String sql = """
                SELECT COUNT(*)
                FROM policy.acknowledgement
                WHERE policy_id = :policyId
                  AND version_acknowledged = :version
                  AND employee_id IN (:employeeIds)
                """;

            Map<String, Object> params = Map.of(
                "policyId", campaign.getPolicyId(),
                "version", campaign.getPolicyVersion(),
                "employeeIds", audienceIds
            );

            Long count = jdbc.queryForObject(sql, params, Long.class);
            ackedCount = count != null ? count.intValue() : 0;
        }

        // Per-department breakdown — HR_ADMIN only (small departments would let a
        // broader HR reader infer individual non-compliance). Aggregate totals stay for READ_HR.
        List<DepartmentProgress> byDepartment = new ArrayList<>();
        if (campaign.getAudience() == CampaignAudience.ALL && currentRequest.hasRole("HR_ADMIN")) {
            String deptSql = """
                SELECT
                    ou.id as dept_id,
                    ou.name as dept_name,
                    COUNT(DISTINCT e.id) as total,
                    COUNT(DISTINCT pa.employee_id) as acked
                FROM core_hr.employee e
                JOIN organization.org_unit ou ON e.org_unit_id = ou.id
                LEFT JOIN policy.acknowledgement pa
                    ON pa.employee_id = e.id
                    AND pa.policy_id = :policyId
                    AND pa.version_acknowledged = :version
                WHERE e.tenant_id = :tenantId
                  AND e.employment_status = 'ACTIVE'
                GROUP BY ou.id, ou.name
                ORDER BY ou.name
                """;

            MapSqlParameterSource deptParams = new MapSqlParameterSource()
                .addValue("policyId", campaign.getPolicyId())
                .addValue("version", campaign.getPolicyVersion())
                .addValue("tenantId", tenantId);

            List<Map<String, Object>> deptRows = jdbc.queryForList(deptSql, deptParams);
            for (Map<String, Object> row : deptRows) {
                byDepartment.add(new DepartmentProgress(
                    (UUID) row.get("dept_id"),
                    (String) row.get("dept_name"),
                    ((Number) row.get("total")).intValue(),
                    ((Number) row.get("acked")).intValue()
                ));
            }
        }

        return new CampaignProgress(campaignId, totalAudience, ackedCount, byDepartment);
    }

    /**
     * List all campaigns for current tenant.
     */
    public List<AcknowledgementCampaign> listAll() {
        String tenantId = TenantContext.current();
        return campaignRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    /**
     * List campaigns by status.
     */
    public List<AcknowledgementCampaign> listByStatus(CampaignStatus status) {
        String tenantId = TenantContext.current();
        return campaignRepo.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
    }

    /**
     * Get single campaign.
     */
    public AcknowledgementCampaign get(UUID campaignId) {
        String tenantId = TenantContext.current();
        return campaignRepo.findByIdAndTenantId(campaignId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private List<Map<String, Object>> fetchAudienceEmployees(AcknowledgementCampaign campaign) {
        String sql;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tenantId", campaign.getTenantId());

        if (campaign.getAudience() == CampaignAudience.ALL) {
            sql = """
                SELECT id, username
                FROM core_hr.employee
                WHERE tenant_id = :tenantId
                  AND employment_status = 'ACTIVE'
                """;
        } else {
            sql = """
                SELECT id, username
                FROM core_hr.employee
                WHERE tenant_id = :tenantId
                  AND employment_status = 'ACTIVE'
                  AND org_unit_id = :orgUnitId
                """;
            params.addValue("orgUnitId", campaign.getAudienceRef());
        }

        return jdbc.queryForList(sql, params);
    }

    private String fetchPolicyTitle(UUID policyId) {
        return policyRepo.findById(policyId)
            .map(p -> p.getTitle())
            .orElse("Policy");
    }

    // ── DTOs ───────────────────────────────────────────────────────────────────

    public record CampaignProgress(
        UUID campaignId,
        int totalAudience,
        int ackedCount,
        List<DepartmentProgress> byDepartment
    ) {}

    public record DepartmentProgress(
        UUID deptId,
        String deptName,
        int total,
        int acked
    ) {}
}
