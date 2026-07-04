package az.millers.hcm.corehr.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import az.millers.hcm.corehr.api.dto.EmployeeSearchFilter;
import az.millers.hcm.corehr.domain.Employee;
import jakarta.persistence.criteria.Predicate;

/**
 * Composes {@link Specification}s for the M69 advanced search (P1-15).
 *
 * <p>Builds a single conjunctive Spec from an {@link EmployeeSearchFilter} —
 * every supplied dimension contributes a predicate, missing dimensions are
 * silently skipped. Combines with {@code AccessScopeService}'s scope filter
 * via {@link Specification#and(Specification)} in {@code EmployeeService}.
 *
 * <p>The factory style keeps the search predicates declarative and easy to
 * extend — adding a new filter dimension (e.g. skill match in a later phase)
 * is one new branch in {@link #from(EmployeeSearchFilter)}, no service code
 * change.
 */
public final class EmployeeSpecifications {

    private EmployeeSpecifications() {}

    /**
     * Builds an AND-of-predicates {@link Specification} from the filter. Any
     * unsupplied dimension is silently skipped. Returns a no-op Spec (matches
     * every row) when the filter is fully empty.
     */
    public static Specification<Employee> from(EmployeeSearchFilter f) {
        if (f == null) return matchAll();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(f.search())) {
                String like = "%" + f.search().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")),   like),
                        cb.like(cb.lower(root.get("lastName")),    like),
                        cb.like(cb.lower(root.get("employeeNo")),  like),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like)));
            }
            Set<?> statuses = f.statuses();
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("employmentStatus").in(statuses));
            }
            if (f.employmentType() != null) {
                predicates.add(cb.equal(root.get("employmentType"), f.employmentType()));
            }
            if (StringUtils.hasText(f.nationality())) {
                predicates.add(cb.equal(root.get("nationality"), f.nationality().toUpperCase()));
            }
            if (f.maritalStatus() != null) {
                predicates.add(cb.equal(root.get("maritalStatus"), f.maritalStatus()));
            }
            if (f.departmentOrgUnitId() != null) {
                predicates.add(cb.equal(root.get("orgUnitId"), f.departmentOrgUnitId()));
            }
            if (StringUtils.hasText(f.departmentName())) {
                String like = "%" + f.departmentName().toLowerCase() + "%";
                predicates.add(cb.like(
                        cb.lower(cb.coalesce(root.get("departmentName"), "")), like));
            }
            if (f.positionId() != null) {
                predicates.add(cb.equal(root.get("positionId"), f.positionId()));
            }
            if (f.managerId() != null) {
                predicates.add(cb.equal(root.get("managerId"), f.managerId()));
            }
            if (f.leaveGroupId() != null) {
                predicates.add(cb.equal(root.get("leaveGroupId"), f.leaveGroupId()));
            }
            if (StringUtils.hasText(f.costCentre())) {
                predicates.add(cb.equal(root.get("costCentre"), f.costCentre()));
            }
            if (f.hireDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("hireDate"), f.hireDateFrom()));
            }
            if (f.hireDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("hireDate"), f.hireDateTo()));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * ABAC scope predicate — restricts the result set to the given employee IDs.
     * Used by {@code EmployeeService.list} when the caller has a non-unrestricted
     * scope (DEPARTMENT_MANAGER → reporting chain, HR_SPECIALIST → org-unit branch).
     */
    public static Specification<Employee> inScope(Set<UUID> allowedIds) {
        if (allowedIds == null || allowedIds.isEmpty()) {
            return (root, q, cb) -> cb.disjunction(); // matches nothing
        }
        return (root, q, cb) -> root.get("id").in(allowedIds);
    }

    public static Specification<Employee> matchAll() {
        return (root, q, cb) -> cb.conjunction();
    }
}
