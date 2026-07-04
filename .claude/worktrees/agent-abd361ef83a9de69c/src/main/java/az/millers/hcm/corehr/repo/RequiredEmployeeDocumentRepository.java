package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.RequiredDocumentStatus;
import az.millers.hcm.corehr.domain.RequiredEmployeeDocument;

/** M262 — required employee document repo (PRD §29). */
public interface RequiredEmployeeDocumentRepository extends JpaRepository<RequiredEmployeeDocument, UUID> {

    /** All requirements for an employee, most recent first. */
    List<RequiredEmployeeDocument> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    /** Pending requirements only — drives the self-service "you owe HR these" widget. */
    List<RequiredEmployeeDocument> findByEmployeeIdAndStatusOrderByRequiredByDateAsc(
            UUID employeeId, RequiredDocumentStatus status);
}
