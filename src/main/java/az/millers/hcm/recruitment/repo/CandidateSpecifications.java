package az.millers.hcm.recruitment.repo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.CandidatePoolStatus;
import jakarta.persistence.criteria.Predicate;

/**
 * Talent-pool search predicates (M87).
 *
 * <p>Replaces a JPQL {@code where (:status is null or ...) and (:ids is null or
 * ...) and (:q is null or ...)} chain. All three parameters are null on the
 * default search — no status filter, no tag filter, no text — and Postgres
 * cannot type a placeholder tested only for nullity, so the unfiltered pool
 * search, the one the page opens with, was rejected outright with 42P18.
 */
public final class CandidateSpecifications {

    private CandidateSpecifications() {}

    public static Specification<Candidate> poolSearch(
            CandidatePoolStatus status, Collection<UUID> ids, String q) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("poolStatus"), status));
            }
            // An empty id set means "the tag filter matched nothing", which is
            // not the same as "no tag filter" — the caller short-circuits that
            // case before we get here, so only a non-empty set narrows.
            if (ids != null && !ids.isEmpty()) {
                predicates.add(root.get("id").in(ids));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("skills"), "")), like)));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
