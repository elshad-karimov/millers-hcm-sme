package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.DocumentCategory;

/**
 * M439 — Document category repository.
 */
public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, UUID> {

    List<DocumentCategory> findByTenantIdAndActiveTrueOrderBySortOrderAsc(String tenantId);
}
