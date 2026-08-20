package az.millers.hcm.leave.service.entitlement;

import java.time.LocalDate;
import java.util.List;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmployeeDependent;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.staffing.domain.Position;

/**
 * M151 — everything the resolvers are allowed to see when computing one
 * employee's entitlement for one leave type and year.
 *
 * <p>Assembled once per employee by
 * {@code LeaveEntitlementComponentService} and handed to every resolver, so a
 * recalculation reads each driver exactly once instead of every resolver
 * issuing its own queries.
 *
 * @param employee    the employee being resolved
 * @param leaveType   the type being resolved, carrying the tenant's config
 * @param year        the leave year
 * @param position    the employee's position, or null when unassigned —
 *                    hazardous leave then resolves to nothing
 * @param dependents  active dependents; only children are consulted
 * @param asOf        the date the drivers are evaluated at. This is the first
 *                    day of {@code year}, not today: entitlement is fixed for
 *                    the whole leave year, so an employee crossing a seniority
 *                    threshold in March gets the uplift next January rather
 *                    than having a live balance rewritten under them.
 */
public record EntitlementContext(
        Employee employee,
        LeaveType leaveType,
        int year,
        Position position,
        List<EmployeeDependent> dependents,
        LocalDate asOf) {

    /** Convenience for resolvers that key off the tenant's grade taxonomy. */
    public String positionClassification() {
        return employee == null ? null : employee.getPositionClassification();
    }
}
