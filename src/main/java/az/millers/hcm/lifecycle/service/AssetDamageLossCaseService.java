package az.millers.hcm.lifecycle.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.lifecycle.domain.AssetDamageLossCase;
import az.millers.hcm.lifecycle.domain.AssetDamageLossCaseStatus;
import az.millers.hcm.lifecycle.domain.AssetDamageLossCaseType;
import az.millers.hcm.lifecycle.repo.AssetDamageLossCaseRepository;
import az.millers.hcm.payroll.domain.PayrollDeduction;
import az.millers.hcm.payroll.repo.PayrollDeductionRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

/**
 * M458 — Asset damage/loss case service. On APPROVE with deduction: create
 * PayrollDeduction (non-fatal bridge). Amounts HR/Finance-only (confidential).
 */
@Service
public class AssetDamageLossCaseService {
    private static final String TENANT = "default";
    private static final String MODULE = "lifecycle";
    private static final String ENTITY = "AssetDamageLossCase";

    private final AssetDamageLossCaseRepository repository;
    private final PayrollDeductionRepository deductionRepo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final NamedParameterJdbcTemplate jdbc;

    public AssetDamageLossCaseService(AssetDamageLossCaseRepository repository, PayrollDeductionRepository deductionRepo,
                                      AuditService audit, CurrentRequest currentRequest, NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.deductionRepo = deductionRepo;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AssetDamageLossCase> listOpen() {
        return repository.findByTenantIdAndStatusOrderByReportedAtDesc(TENANT, AssetDamageLossCaseStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public List<AssetDamageLossCase> listAll() {
        return repository.findByTenantIdOrderByReportedAtDesc(TENANT);
    }

    @Transactional(readOnly = true)
    public AssetDamageLossCase get(UUID id) {
        return repository.findById(id)
                .filter(c -> TENANT.equals(c.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
    }

    @Transactional
    public AssetDamageLossCase report(UUID assetId, UUID employeeId, AssetDamageLossCaseType caseType,
                                      String description, BigDecimal estimatedAmount) {
        String caseNo = BusinessNumbers.format("ADL", 5, jdbc.queryForObject("SELECT nextval('lifecycle.asset_damage_loss_case_no_seq')", Map.of(), Long.class));

        AssetDamageLossCase c = new AssetDamageLossCase();
        c.setTenantId(TENANT);
        c.setCaseNo(caseNo);
        c.setAssetId(assetId);
        c.setEmployeeId(employeeId);
        c.setCaseType(caseType);
        c.setDescription(description);
        c.setEstimatedAmount(estimatedAmount);
        c.setStatus(AssetDamageLossCaseStatus.OPEN);
        c.setReportedBy(currentRequest.username());
        c.setCreatedBy(currentRequest.username());
        c.setUpdatedBy(currentRequest.username());
        AssetDamageLossCase saved = repository.save(c);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "REPORT", null, saved);
        return saved;
    }

    @Transactional
    public AssetDamageLossCase approve(UUID id, Boolean deductionProposed, BigDecimal deductionAmount) {
        AssetDamageLossCase c = get(id);
        if (c.getStatus() != AssetDamageLossCaseStatus.OPEN) {
            throw new BadRequestException("Only OPEN cases can be approved");
        }

        c.setStatus(AssetDamageLossCaseStatus.APPROVED);
        c.setDeductionProposed(deductionProposed != null && deductionProposed);
        c.setDeductionAmount(deductionAmount);
        c.setApprovedBy(currentRequest.username());
        c.setApprovedAt(OffsetDateTime.now());
        c.setUpdatedBy(currentRequest.username());

        // Non-fatal bridge: create PayrollDeduction if deduction proposed
        if (Boolean.TRUE.equals(c.getDeductionProposed()) && deductionAmount != null && deductionAmount.compareTo(BigDecimal.ZERO) > 0) {
            try {
                PayrollDeduction deduction = new PayrollDeduction();
                deduction.setId(UUID.randomUUID());
                deduction.setTenantId(TENANT);
                deduction.setEmployeeId(c.getEmployeeId());
                deduction.setDeductionType("ASSET_DAMAGE");
                deduction.setDescription("Asset damage/loss case: " + c.getCaseNo());
                deduction.setAmountPerPeriod(deductionAmount);
                deduction.setTotalAmount(deductionAmount);
                deduction.setRecoveredAmount(BigDecimal.ZERO);
                int currentYear = java.time.LocalDate.now().getYear();
                int currentMonth = java.time.LocalDate.now().getMonthValue();
                deduction.setStartPeriodYear(currentYear);
                deduction.setStartPeriodMonth(currentMonth);
                deduction.setStatus("ACTIVE");
                PayrollDeduction savedDeduction = deductionRepo.save(deduction);
                c.setPayrollDeductionId(savedDeduction.getId());
                audit.record("payroll", "PayrollDeduction", savedDeduction.getId().toString(), "CREATE_FROM_ASSET_DAMAGE",
                        null, Map.of("caseNo", c.getCaseNo(), "amount", deductionAmount));
            } catch (Exception e) {
                // Non-fatal: log but continue
                audit.record(MODULE, ENTITY, id.toString(), "DEDUCTION_FAILED", null, Map.of("error", e.getMessage()));
            }
        }

        AssetDamageLossCase saved = repository.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "APPROVE", null, Map.of("deductionProposed", c.getDeductionProposed()));
        return saved;
    }

    @Transactional
    public AssetDamageLossCase reject(UUID id, String reason) {
        AssetDamageLossCase c = get(id);
        if (c.getStatus() != AssetDamageLossCaseStatus.OPEN) {
            throw new BadRequestException("Only OPEN cases can be rejected");
        }
        c.setStatus(AssetDamageLossCaseStatus.REJECTED);
        c.setNotes(reason);
        c.setUpdatedBy(currentRequest.username());
        AssetDamageLossCase saved = repository.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "REJECT", null, Map.of("reason", reason));
        return saved;
    }

    @Transactional
    public AssetDamageLossCase close(UUID id, String notes) {
        AssetDamageLossCase c = get(id);
        c.setStatus(AssetDamageLossCaseStatus.CLOSED);
        if (notes != null && !notes.isBlank()) {
            c.setNotes(c.getNotes() == null ? notes : c.getNotes() + "\n" + notes);
        }
        c.setUpdatedBy(currentRequest.username());
        AssetDamageLossCase saved = repository.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "CLOSE", null, null);
        return saved;
    }
}
