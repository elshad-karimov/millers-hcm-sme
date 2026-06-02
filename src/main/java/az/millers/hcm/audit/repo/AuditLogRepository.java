package az.millers.hcm.audit.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.audit.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntityNameAndEntityIdOrderByCreatedAtDesc(String entityName, String entityId);

    /**
     * Recent activity feed (M80 / P2-32). Filters by optional module / entity
     * / actor and caps with {@link Pageable}. Native query reads
     * {@code audit.audit_log} directly since the entity is partitioned and
     * Hibernate's JPQL planner can't push the LIMIT through the partition
     * router cheaply.
     */
    @Query("""
            select a from AuditLog a
            where (:module is null or a.module = :module)
              and (:entityName is null or a.entityName = :entityName)
              and (:actor is null or a.actor = :actor)
            order by a.createdAt desc
            """)
    List<AuditLog> findRecent(String module, String entityName, String actor, Pageable pageable);
}
