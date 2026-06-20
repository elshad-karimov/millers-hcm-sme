package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.lifecycle.domain.NoticePeriodRule;

public interface NoticePeriodRuleRepository extends JpaRepository<NoticePeriodRule, UUID> {

    List<NoticePeriodRule> findByActiveOrderByEmploymentTypeAscOnProbationAsc(boolean active);

    /**
     * Best-match: probation rules beat type-specific rules beat catch-all.
     * employmentType is passed as its name() string to avoid SpEL issues in native queries.
     */
    @Query(value = """
        SELECT * FROM lifecycle.notice_period_rule
        WHERE active = true
        ORDER BY
            CASE
                WHEN on_probation = :onProbation AND employment_type = :employmentType THEN 1
                WHEN on_probation = false         AND employment_type = :employmentType THEN 2
                WHEN on_probation = :onProbation  AND employment_type IS NULL           THEN 3
                ELSE 4
            END
        LIMIT 1
        """, nativeQuery = true)
    Optional<NoticePeriodRule> findBestMatch(
            @Param("employmentType") String employmentType,
            @Param("onProbation") boolean onProbation);
}
