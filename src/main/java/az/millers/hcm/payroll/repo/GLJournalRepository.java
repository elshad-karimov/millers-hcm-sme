package az.millers.hcm.payroll.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.payroll.domain.GLJournal;

public interface GLJournalRepository extends JpaRepository<GLJournal, UUID> {

    Optional<GLJournal> findByRunId(UUID runId);

    void deleteByRunId(UUID runId);
}
