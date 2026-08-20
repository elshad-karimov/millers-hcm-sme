package az.millers.hcm.audit.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.audit.domain.AuditLog;

/**
 * Audit-log reads.
 *
 * <p>The filtered queries are {@link JpaSpecificationExecutor} calls built by
 * {@code AuditLogSpecifications}, not JPQL. They used to be
 * {@code where (:param is null or col = :param)} chains, which Postgres rejects
 * outright when the parameters are null — see that class for the detail.
 */
public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByEntityNameAndEntityIdOrderByCreatedAtDesc(String entityName, String entityId);

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
