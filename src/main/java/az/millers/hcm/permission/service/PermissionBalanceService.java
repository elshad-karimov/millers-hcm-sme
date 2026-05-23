package az.millers.hcm.permission.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.permission.api.dto.PermissionBalanceAdjustment;
import az.millers.hcm.permission.api.dto.PermissionBalanceResponse;
import az.millers.hcm.permission.domain.PermissionBalance;
import az.millers.hcm.permission.domain.PermissionType;
import az.millers.hcm.permission.repo.PermissionBalanceRepository;
import az.millers.hcm.permission.repo.PermissionTypeRepository;

@Service
public class PermissionBalanceService {

    private static final String MODULE = "PERMISSION";

    private final PermissionBalanceRepository balances;
    private final PermissionTypeRepository types;
    private final AuditService audit;

    public PermissionBalanceService(PermissionBalanceRepository balances,
                                     PermissionTypeRepository types,
                                     AuditService audit) {
        this.balances = balances;
        this.types = types;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PermissionBalance> listForEmployee(UUID employeeId, int year) {
        return balances.findByEmployeeIdAndYearOrderByPermissionTypeId(employeeId, year);
    }

    @Transactional(readOnly = true)
    public List<PermissionBalance> listForYear(int year) {
        return balances.findByYearOrderByEmployeeIdAscPermissionTypeIdAsc(year);
    }

    @Transactional
    public PermissionBalance ensureBalance(UUID employeeId, UUID permissionTypeId, int year) {
        return balances.findByEmployeeIdAndPermissionTypeIdAndYear(employeeId, permissionTypeId, year)
                .orElseGet(() -> initialiseBalance(employeeId, permissionTypeId, year));
    }

    @Transactional
    public PermissionBalance applyAdjustment(PermissionBalanceAdjustment req) {
        PermissionBalance b = ensureBalance(req.employeeId(), req.permissionTypeId(), req.year());
        PermissionBalanceResponse before = PermissionBalanceResponse.from(b);
        b.setAdjustmentHours(b.getAdjustmentHours().add(req.deltaHours()));
        b.setLastRecalculatedAt(OffsetDateTime.now());
        PermissionBalance saved = balances.save(b);
        audit.record(MODULE, "PermissionBalance", saved.getId().toString(),
                "ADJUST", before, PermissionBalanceResponse.from(saved));
        return saved;
    }

    @Transactional
    public PermissionBalance reserve(UUID employeeId, UUID permissionTypeId, int year,
                                      BigDecimal hours, boolean enforceLimit) {
        PermissionBalance b = ensureBalance(employeeId, permissionTypeId, year);
        if (enforceLimit && b.remaining().compareTo(hours) < 0) {
            throw new BadRequestException(
                    "Insufficient remaining permission hours: requested " + hours
                            + ", available " + b.remaining());
        }
        b.setReservedHours(b.getReservedHours().add(hours));
        b.setLastRecalculatedAt(OffsetDateTime.now());
        return balances.save(b);
    }

    @Transactional
    public PermissionBalance commit(UUID employeeId, UUID permissionTypeId, int year, BigDecimal hours) {
        PermissionBalance b = ensureBalance(employeeId, permissionTypeId, year);
        b.setReservedHours(b.getReservedHours().subtract(hours).max(BigDecimal.ZERO));
        b.setUsedHours(b.getUsedHours().add(hours));
        b.setLastRecalculatedAt(OffsetDateTime.now());
        return balances.save(b);
    }

    @Transactional
    public PermissionBalance release(UUID employeeId, UUID permissionTypeId, int year, BigDecimal hours) {
        PermissionBalance b = ensureBalance(employeeId, permissionTypeId, year);
        b.setReservedHours(b.getReservedHours().subtract(hours).max(BigDecimal.ZERO));
        b.setLastRecalculatedAt(OffsetDateTime.now());
        return balances.save(b);
    }

    private PermissionBalance initialiseBalance(UUID employeeId, UUID permissionTypeId, int year) {
        PermissionType type = types.findById(permissionTypeId)
                .orElseThrow(() -> new BadRequestException(
                        "Permission type not found: " + permissionTypeId));
        PermissionBalance b = new PermissionBalance();
        b.setEmployeeId(employeeId);
        b.setPermissionTypeId(permissionTypeId);
        b.setYear(year);
        b.setLimitHours(type.getAnnualLimitHours() == null ? BigDecimal.ZERO : type.getAnnualLimitHours());
        return balances.save(b);
    }
}
