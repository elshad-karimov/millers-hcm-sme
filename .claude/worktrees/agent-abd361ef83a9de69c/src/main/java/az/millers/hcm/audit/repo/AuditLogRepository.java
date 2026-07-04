package az.millers.hcm.audit.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.audit.domain.AuditLog;

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

    /**
     * M114 — audit-log browser query. Supports the full filter set the UI
     * exposes (module / entity / entity-id / action / actor + date range).
     * The {@code createdAt} predicates are first so Postgres can prune
     * partitions before walking the index. Returned in descending time
     * order — newest entries first.
     */
    @Query("""
            select a from AuditLog a
            where (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt < :to)
              and (:module is null or a.module = :module)
              and (:entityName is null or a.entityName = :entityName)
              and (:entityId is null or a.entityId = :entityId)
              and (:action is null or a.action = :action)
              and (:actor is null or a.actor = :actor)
            order by a.createdAt desc
            """)
    Page<AuditLog> search(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("module") String module,
            @Param("entityName") String entityName,
            @Param("entityId") String entityId,
            @Param("action") String action,
            @Param("actor") String actor,
            Pageable pageable);

    /**
     * M114 — distinct values for the filter dropdowns. Capped server-side
     * to keep the response small; the UI's dropdowns are search-as-you-type
     * for the long tail.
     */
    @Query("select distinct a.module from AuditLog a order by a.module asc")
    List<String> distinctModules();

    @Query("select distinct a.entityName from AuditLog a where a.module = :module order by a.entityName asc")
    List<String> distinctEntitiesIn(@Param("module") String module);

    @Query("select distinct a.action from AuditLog a where a.module = :module order by a.action asc")
    List<String> distinctActionsIn(@Param("module") String module);
}
