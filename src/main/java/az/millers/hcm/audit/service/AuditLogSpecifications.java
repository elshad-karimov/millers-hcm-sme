package az.millers.hcm.audit.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import az.millers.hcm.audit.domain.AuditLog;
import jakarta.persistence.criteria.Predicate;

/**
 * Composes {@link Specification}s for the audit-log browser (M114) and the
 * activity feed (M80).
 *
 * <p>Replaces a JPQL {@code where (:param is null or col = :param)} chain that
 * could not run at all. With every filter empty — the default view — Hibernate
 * emitted a bare {@code ? is null} for each one, and Postgres cannot infer a
 * type for a placeholder whose only context is a null test:
 * {@code ERROR: could not determine data type of parameter $2}, 42P18, on every
 * single request. The audit log was unopenable.
 *
 * <p>Building the predicates instead of neutralising them also gives the
 * planner a WHERE clause it can actually use. {@code audit.audit_log} is
 * append-only and partitioned, and {@code (? is null or created_at >= ?)} is
 * opaque to partition pruning — the very thing the original query's comment
 * said it wanted. An absent filter now contributes no predicate at all.
 *
 * <p>Tenant scoping is not expressed here and must not be: {@link AuditLog} is
 * annotated {@code @TenantId}, so Hibernate adds {@code tenant_id = ?} to every
 * query for the entity, Criteria queries included.
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    /** Matches every row — the base a filtered Spec is ANDed onto. */
    public static Specification<AuditLog> matchAll() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * AND-of-predicates over the browser's filter set. Any unsupplied
     * dimension is skipped rather than compared against null.
     *
     * @param from inclusive lower bound on {@code createdAt}, or null
     * @param to   exclusive upper bound on {@code createdAt}, or null
     */
    public static Specification<AuditLog> forFilter(
            OffsetDateTime from,
            OffsetDateTime to,
            String module,
            String entityName,
            String entityId,
            String action,
            String actor) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Time bounds first: they are what lets Postgres prune partitions.
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            addEquals(predicates, cb, root.get("module"), module);
            addEquals(predicates, cb, root.get("entityName"), entityName);
            addEquals(predicates, cb, root.get("entityId"), entityId);
            addEquals(predicates, cb, root.get("action"), action);
            addEquals(predicates, cb, root.get("actor"), actor);

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** The activity feed's narrower filter set — same null handling. */
    public static Specification<AuditLog> forActivityFeed(
            String module, String entityName, String actor) {
        return forFilter(null, null, module, entityName, null, null, actor);
    }

    private static void addEquals(List<Predicate> predicates,
                                   jakarta.persistence.criteria.CriteriaBuilder cb,
                                   jakarta.persistence.criteria.Path<String> path,
                                   String value) {
        if (StringUtils.hasText(value)) {
            predicates.add(cb.equal(path, value));
        }
    }
}
