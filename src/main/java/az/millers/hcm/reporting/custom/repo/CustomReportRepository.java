package az.millers.hcm.reporting.custom.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.reporting.custom.domain.CustomReport;

public interface CustomReportRepository extends JpaRepository<CustomReport, UUID> {

    /**
     * List "mine + shared". Owner-rows first so the user sees their own
     * library at the top, then the org-wide shared library underneath.
     */
    @Query("""
        SELECT r FROM CustomReport r
         WHERE r.ownerUser = :user
            OR r.shared = true
         ORDER BY (CASE WHEN r.ownerUser = :user THEN 0 ELSE 1 END), r.updatedAt DESC
        """)
    List<CustomReport> findVisibleTo(@Param("user") String user);

    Optional<CustomReport> findByOwnerUserAndNameIgnoreCase(String ownerUser, String name);
}
