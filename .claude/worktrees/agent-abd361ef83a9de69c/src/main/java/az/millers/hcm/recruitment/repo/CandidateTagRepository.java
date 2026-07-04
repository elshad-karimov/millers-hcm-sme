package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.CandidateTag;

public interface CandidateTagRepository extends JpaRepository<CandidateTag, UUID> {

    List<CandidateTag> findByCandidateIdOrderByTagAsc(UUID candidateId);

    /**
     * Distinct lower-cased tag list across the whole pool — used by the
     * SPA to populate the multi-select filter. Native to avoid Hibernate's
     * dialect-specific lower() handling.
     */
    @Query(value = """
            SELECT DISTINCT lower(tag) AS tag
            FROM recruitment.candidate_tag
            ORDER BY tag
            """, nativeQuery = true)
    List<String> distinctTags();

    /**
     * Candidate ids that carry <em>every</em> tag in {@code tags}
     * (case-insensitive). HAVING count ensures AND semantics —
     * a candidate tagged "React" + "Senior" passes the filter
     * `tags = [react, senior]`.
     */
    @Query(value = """
            SELECT candidate_id
            FROM recruitment.candidate_tag
            WHERE lower(tag) IN (:tags)
            GROUP BY candidate_id
            HAVING COUNT(DISTINCT lower(tag)) = :tagCount
            """, nativeQuery = true)
    List<UUID> candidateIdsHavingAllTags(java.util.Collection<String> tags, int tagCount);
}
