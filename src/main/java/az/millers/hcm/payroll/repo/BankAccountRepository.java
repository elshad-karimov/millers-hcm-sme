package az.millers.hcm.payroll.repo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.payroll.domain.BankAccount;

/**
 * Bank-account repository (M74 / P2-06). Migrated from 1:1 to multi-row
 * per employee — the legacy {@link #findByEmployeeId(UUID)} is preserved
 * as a backward-compat shortcut returning the primary active account, so
 * callers like {@code BankFileService} keep working without a refactor.
 */
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    /** All accounts (primary + secondary) for an employee, ordered with primary first. */
    List<BankAccount> findByEmployeeIdOrderByPrimaryDescCreatedAtAsc(UUID employeeId);

    /** Active accounts only — what the payroll engine consumes when paying. */
    @Query("""
            select b from BankAccount b
            where b.employeeId = :employeeId
              and b.active = true
            order by b.primary desc, b.createdAt asc
            """)
    List<BankAccount> findActiveByEmployeeId(UUID employeeId);

    /**
     * Backward-compat: returns the employee's primary active account.
     * Pre-M74 callers (BankFileService, payroll bank-file generation) hit
     * this signature; preserving it keeps the rest of the payroll module
     * working through the multi-account migration.
     */
    @Query("""
            select b from BankAccount b
            where b.employeeId = :employeeId
              and b.active = true
              and b.primary = true
            """)
    Optional<BankAccount> findByEmployeeId(UUID employeeId);

    /**
     * Sum of active split percentages — used by the service-layer validator
     * to confirm a new / updated row keeps the employee total at 100.
     * Excludes the row whose id is supplied (for update operations).
     */
    @Query("""
            select coalesce(sum(b.salarySplitPercent), 0)
            from BankAccount b
            where b.employeeId = :employeeId
              and b.active = true
            """)
    BigDecimal sumActiveSplitForEmployee(UUID employeeId);

    /**
     * As {@link #sumActiveSplitForEmployee(UUID)}, skipping one row.
     *
     * <p>Two methods rather than one with a nullable {@code excludeId}, because
     * the single-method form was {@code (:excludeId is null or b.id <> :excludeId)}
     * and the create path passes null. Postgres cannot infer a type for a
     * placeholder whose only context is a null test and rejects the statement
     * outright — 42P18, "could not determine data type of parameter" — so
     * adding a bank account failed before its split could ever be validated.
     */
    @Query("""
            select coalesce(sum(b.salarySplitPercent), 0)
            from BankAccount b
            where b.employeeId = :employeeId
              and b.active = true
              and b.id <> :excludeId
            """)
    BigDecimal sumActiveSplitForEmployeeExcluding(UUID employeeId, UUID excludeId);
}
