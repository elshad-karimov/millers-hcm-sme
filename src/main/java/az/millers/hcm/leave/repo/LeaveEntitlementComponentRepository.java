package az.millers.hcm.leave.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.leave.domain.EntitlementComponentCode;
import az.millers.hcm.leave.domain.LeaveEntitlementComponent;

/**
 * M151 — entitlement component access. Every query is tenant-scoped by the
 * Hibernate {@code @TenantId} filter on the entity.
 */
public interface LeaveEntitlementComponentRepository
        extends JpaRepository<LeaveEntitlementComponent, UUID> {

    /** The full breakdown behind one balance — backs the entitlement tab. */
    List<LeaveEntitlementComponent> findByEmployeeIdAndLeaveTypeIdAndYearOrderByComponentCode(
            UUID employeeId, UUID leaveTypeId, int year);

    /** Every component the employee holds in a year, across leave types. */
    List<LeaveEntitlementComponent> findByEmployeeIdAndYear(UUID employeeId, int year);

    /** Upsert target for a recalculation. */
    Optional<LeaveEntitlementComponent> findByEmployeeIdAndLeaveTypeIdAndYearAndComponentCode(
            UUID employeeId, UUID leaveTypeId, int year, EntitlementComponentCode componentCode);

    /**
     * Workforce liability by component — "how many harmful-conditions days are
     * we carrying for 2026?". Aggregated in the DB so the report never pulls
     * a row per employee.
     */
    @Query("""
           select c.componentCode, sum(c.days)
             from LeaveEntitlementComponent c
            where c.year = :year
            group by c.componentCode
           """)
    List<Object[]> sumDaysByComponentForYear(int year);
}
