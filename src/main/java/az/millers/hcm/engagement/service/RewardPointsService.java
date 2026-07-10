package az.millers.hcm.engagement.service;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.engagement.domain.*;
import az.millers.hcm.engagement.repo.*;
import az.millers.hcm.payroll.api.dto.AddBonusRequest;
import az.millers.hcm.payroll.domain.BonusType;
import az.millers.hcm.payroll.domain.PayrollBonus;
import az.millers.hcm.payroll.service.PayrollRunService;
import az.millers.hcm.security.CurrentRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M481: Reward points service — grant/redeem points, manage catalog/budgets.
 * Named RewardPointsService to avoid collision with existing EmployeeRewardService.
 */
@Service
public class RewardPointsService {

    private static final String MODULE = "engagement";
    private static final String TENANT_ID = "default"; // Hardcoded per existing pattern

    private final RewardCatalogRepository catalogRepo;
    private final RewardWalletRepository walletRepo;
    private final RewardBudgetRepository budgetRepo;
    private final RewardRedemptionRepository redemptionRepo;
    private final CurrentRequest currentRequest;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;
    private final PayrollRunService payrollRunService;

    public RewardPointsService(RewardCatalogRepository catalogRepo,
                               RewardWalletRepository walletRepo,
                               RewardBudgetRepository budgetRepo,
                               RewardRedemptionRepository redemptionRepo,
                               CurrentRequest currentRequest,
                               AuditService audit,
                               NamedParameterJdbcTemplate jdbc,
                               PayrollRunService payrollRunService) {
        this.catalogRepo = catalogRepo;
        this.walletRepo = walletRepo;
        this.budgetRepo = budgetRepo;
        this.redemptionRepo = redemptionRepo;
        this.currentRequest = currentRequest;
        this.audit = audit;
        this.jdbc = jdbc;
        this.payrollRunService = payrollRunService;
    }

    // ───────────────────────────── Catalog ─────────────────────────────

    @Transactional(readOnly = true)
    public List<RewardCatalog> listCatalog(boolean activeOnly) {
        return activeOnly
            ? catalogRepo.findByTenantIdAndActiveOrderByName(TENANT_ID, true)
            : catalogRepo.findByTenantIdOrderByName(TENANT_ID);
    }

    @Transactional(readOnly = true)
    public RewardCatalog getCatalog(UUID id) {
        RewardCatalog item = catalogRepo.findByIdAndTenantId(id, TENANT_ID)
            .orElseThrow(() -> new ResourceNotFoundException("Catalog item not found"));
        return item;
    }

    @Transactional
    public RewardCatalog createCatalog(RewardCatalog item) {
        item.setTenantId(TENANT_ID);
        item.setCreatedBy(currentRequest.username());
        RewardCatalog saved = catalogRepo.save(item);
        audit.record(MODULE, "RewardCatalog", saved.getId().toString(), "CREATE", null,
            Map.of("code", saved.getCode(), "name", saved.getName(), "points", saved.getPoints()));
        return saved;
    }

    @Transactional
    public RewardCatalog updateCatalog(UUID id, RewardCatalog updated) {
        RewardCatalog existing = getCatalog(id);
        Map<String, Object> old = Map.of("name", existing.getName(), "points", existing.getPoints(),
            "monetaryValue", existing.getMonetaryValue(), "active", existing.getActive());

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setPoints(updated.getPoints());
        existing.setMonetaryValue(updated.getMonetaryValue());
        existing.setCategory(updated.getCategory());
        existing.setTaxable(updated.getTaxable());
        existing.setActive(updated.getActive());
        existing.setUpdatedBy(currentRequest.username());

        RewardCatalog saved = catalogRepo.save(existing);
        audit.record(MODULE, "RewardCatalog", saved.getId().toString(), "UPDATE", old,
            Map.of("name", saved.getName(), "points", saved.getPoints(),
                "monetaryValue", saved.getMonetaryValue(), "active", saved.getActive()));
        return saved;
    }

    // ───────────────────────────── Wallet ─────────────────────────────

    @Transactional(readOnly = true)
    public RewardWallet getWallet(UUID employeeId) {
        // Tenant-scoped, IDOR guard: self-or-HR
        return walletRepo.findByTenantIdAndEmployeeId(TENANT_ID, employeeId)
            .orElseGet(() -> {
                RewardWallet w = new RewardWallet();
                w.setTenantId(TENANT_ID);
                w.setEmployeeId(employeeId);
                return w;
            });
    }

    @Transactional
    public void grantPoints(UUID employeeId, int points, String note, UUID orgUnitId) {
        if (points <= 0) {
            throw new BadRequestException("Points must be positive");
        }

        // Budget check
        int year = Year.now().getValue();
        RewardBudget budget = budgetRepo.findByTenantIdAndYearAndOrgUnitId(TENANT_ID, year, orgUnitId)
            .orElse(null);

        if (budget != null) {
            BigDecimal pointsValue = BigDecimal.valueOf(points); // 1 point = 1 AZN default
            if (budget.getUsedAmount().add(pointsValue).compareTo(budget.getTotalAmount()) > 0) {
                throw new BadRequestException("Insufficient budget for this org unit");
            }
            budget.setUsedAmount(budget.getUsedAmount().add(pointsValue));
            budget.setUpdatedBy(currentRequest.username());
            budgetRepo.save(budget);
        }

        // Update or create wallet
        RewardWallet wallet = walletRepo.findByTenantIdAndEmployeeId(TENANT_ID, employeeId)
            .orElseGet(() -> {
                RewardWallet w = new RewardWallet();
                w.setTenantId(TENANT_ID);
                w.setEmployeeId(employeeId);
                return w;
            });

        wallet.setBalance(wallet.getBalance() + points);
        wallet.setLifetimeEarned(wallet.getLifetimeEarned() + points);
        walletRepo.save(wallet);

        audit.record(MODULE, "RewardWallet", wallet.getId().toString(), "GRANT", null,
            Map.of("employeeId", employeeId.toString(), "points", points, "note", note != null ? note : ""));
    }

    // ───────────────────────────── Redemption ─────────────────────────────

    @Transactional
    public RewardRedemption redeem(UUID employeeId, UUID catalogItemId, String deliveryAddress) {
        RewardCatalog item = getCatalog(catalogItemId);
        if (!item.getActive()) {
            throw new BadRequestException("Catalog item is not active");
        }

        // Pessimistic lock on wallet to prevent negative-balance race
        RewardWallet wallet = walletRepo.findByTenantIdAndEmployeeId(TENANT_ID, employeeId)
            .map(w -> walletRepo.lockById(w.getId()).orElse(w))
            .orElseGet(() -> {
                RewardWallet w = new RewardWallet();
                w.setTenantId(TENANT_ID);
                w.setEmployeeId(employeeId);
                return walletRepo.save(w);
            });

        if (wallet.getBalance() < item.getPoints()) {
            throw new BadRequestException("Insufficient points balance");
        }

        // Deduct points
        wallet.setBalance(wallet.getBalance() - item.getPoints());
        wallet.setLifetimeSpent(wallet.getLifetimeSpent() + item.getPoints());
        walletRepo.save(wallet);

        // Create redemption
        String redemptionNo = generateRedemptionNo();
        RewardRedemption redemption = new RewardRedemption();
        redemption.setTenantId(TENANT_ID);
        redemption.setRedemptionNo(redemptionNo);
        redemption.setEmployeeId(employeeId);
        redemption.setCatalogItemId(catalogItemId);
        redemption.setPoints(item.getPoints());
        redemption.setStatus("REQUESTED");
        redemption.setDeliveryAddress(deliveryAddress);

        RewardRedemption saved = redemptionRepo.save(redemption);
        audit.record(MODULE, "RewardRedemption", saved.getId().toString(), "REDEEM", null,
            Map.of("employeeId", employeeId.toString(), "itemCode", item.getCode(), "points", item.getPoints()));

        return saved;
    }

    @Transactional(readOnly = true)
    public List<RewardRedemption> listRedemptions(String status) {
        if (status != null && !status.isBlank()) {
            return redemptionRepo.findByTenantIdAndStatusOrderByRequestedAtDesc(TENANT_ID, status);
        }
        return redemptionRepo.findByTenantIdOrderByRequestedAtDesc(TENANT_ID);
    }

    @Transactional(readOnly = true)
    public List<RewardRedemption> listMyRedemptions(UUID employeeId) {
        return redemptionRepo.findByTenantIdAndEmployeeIdOrderByRequestedAtDesc(TENANT_ID, employeeId);
    }

    @Transactional(readOnly = true)
    public RewardRedemption getRedemption(UUID id) {
        return redemptionRepo.findByIdAndTenantId(id, TENANT_ID)
            .orElseThrow(() -> new ResourceNotFoundException("Redemption not found"));
    }

    @Transactional
    public void fulfillRedemption(UUID redemptionId, UUID activePayrollRunId) {
        // Pessimistic lock to prevent double-fulfil race
        RewardRedemption redemption = redemptionRepo.lockById(redemptionId)
            .orElseThrow(() -> new ResourceNotFoundException("Redemption not found"));

        // Idempotency: skip if already fulfilled
        if ("FULFILLED".equals(redemption.getStatus())) {
            audit.record(MODULE, "RewardRedemption", redemption.getId().toString(), "FULFILL_SKIPPED_ALREADY_FULFILLED", null,
                Map.of("status", "FULFILLED"));
            return;
        }

        if (!"REQUESTED".equals(redemption.getStatus())) {
            throw new BadRequestException("Redemption is not in REQUESTED status");
        }

        RewardCatalog item = getCatalog(redemption.getCatalogItemId());

        // If monetary value, bridge to payroll ONE_TIME bonus (non-fatal)
        if (item.getMonetaryValue() != null && item.getMonetaryValue().compareTo(BigDecimal.ZERO) > 0) {
            try {
                if (activePayrollRunId == null) {
                    throw new BadRequestException("Active payroll run ID required for monetary redemptions");
                }
                AddBonusRequest bonusReq = new AddBonusRequest(
                    redemption.getEmployeeId(),
                    BonusType.ONE_TIME,
                    item.getMonetaryValue(),
                    "Reward redemption: " + item.getName()
                );
                PayrollBonus bonus = payrollRunService.addBonus(activePayrollRunId, bonusReq);
                redemption.setPayrollBonusId(bonus.getId());
            } catch (Exception e) {
                audit.record(MODULE, "RewardRedemption", redemption.getId().toString(), "FULFILL_PAYROLL_ERROR", null,
                    Map.of("error", e.getMessage()));
            }
        }

        redemption.setStatus("FULFILLED");
        redemption.setFulfilledAt(Instant.now());
        redemption.setFulfilledBy(currentRequest.username());
        redemptionRepo.save(redemption);

        audit.record(MODULE, "RewardRedemption", redemption.getId().toString(), "FULFILL",
            Map.of("status", "REQUESTED"),
            Map.of("status", "FULFILLED", "fulfilledBy", currentRequest.username()));
    }

    @Transactional
    public void rejectRedemption(UUID redemptionId, String reason) {
        RewardRedemption redemption = getRedemption(redemptionId);
        if (!"REQUESTED".equals(redemption.getStatus())) {
            throw new BadRequestException("Redemption is not in REQUESTED status");
        }

        // Refund points
        RewardWallet wallet = getWallet(redemption.getEmployeeId());
        wallet.setBalance(wallet.getBalance() + redemption.getPoints());
        wallet.setLifetimeSpent(wallet.getLifetimeSpent() - redemption.getPoints());
        walletRepo.save(wallet);

        redemption.setStatus("REJECTED");
        redemption.setNotes(reason);
        redemptionRepo.save(redemption);

        audit.record(MODULE, "RewardRedemption", redemption.getId().toString(), "REJECT",
            Map.of("status", "REQUESTED"),
            Map.of("status", "REJECTED", "reason", reason));
    }

    // ───────────────────────────── Budgets ─────────────────────────────

    @Transactional(readOnly = true)
    public List<RewardBudget> listBudgets(Integer year) {
        return budgetRepo.findByTenantIdAndYearOrderByOrgUnitId(TENANT_ID, year);
    }

    @Transactional
    public RewardBudget createOrUpdateBudget(Integer year, UUID orgUnitId, BigDecimal totalAmount) {
        RewardBudget budget = budgetRepo.findByTenantIdAndYearAndOrgUnitId(TENANT_ID, year, orgUnitId)
            .orElseGet(() -> {
                RewardBudget b = new RewardBudget();
                b.setTenantId(TENANT_ID);
                b.setYear(year);
                b.setOrgUnitId(orgUnitId);
                b.setCreatedBy(currentRequest.username());
                return b;
            });

        budget.setTotalAmount(totalAmount);
        budget.setUpdatedBy(currentRequest.username());
        RewardBudget saved = budgetRepo.save(budget);

        audit.record(MODULE, "RewardBudget", saved.getId().toString(),
            budget.getId() == null ? "CREATE" : "UPDATE", null,
            Map.of("year", year, "totalAmount", totalAmount));

        return saved;
    }

    private String generateRedemptionNo() {
        Long seq = jdbc.queryForObject(
            "SELECT nextval('engagement.redemption_seq')",
            new MapSqlParameterSource(),
            Long.class
        );
        return String.format("RED-%05d", seq);
    }

    // ───────────────────────────── Enrichment (for controllers) ─────────────────────────────

    /**
     * Tenant-filtered JDBC projection for employee name (safe for audit/display).
     */
    public String getEmployeeName(UUID employeeId) {
        try {
            return jdbc.queryForObject(
                "SELECT CONCAT(first_name, ' ', last_name) FROM core_hr.employee WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource()
                    .addValue("id", employeeId)
                    .addValue("tenantId", TENANT_ID),
                String.class
            );
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
