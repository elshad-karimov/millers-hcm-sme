package az.millers.hcm.organization.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.organization.domain.OrgUnit;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

    List<OrgUnit> findByVersionIdOrderBySortOrderAscNameAsc(UUID versionId);

    boolean existsByVersionIdAndCode(UUID versionId, String code);

    boolean existsByParentId(UUID parentId);

    long countByVersionId(UUID versionId);

    /**
     * Transitive descendant ids of {@code rootId}, including the root
     * itself. Used by {@code AccessScopeService} to expand an HR
     * specialist's scope from a single org_unit to the whole subtree
     * (PRD 14.9 — org-unit-based ABAC scoping).
     */
    @Query(value = """
            WITH RECURSIVE chain (id) AS (
                SELECT id FROM organization.org_unit WHERE id = :root
                UNION ALL
                SELECT u.id FROM organization.org_unit u
                JOIN chain c ON u.parent_id = c.id
            )
            SELECT id FROM chain
            """,
            nativeQuery = true)
    List<UUID> descendantUnitIds(UUID root);
}
