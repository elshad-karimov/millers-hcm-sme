package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.payroll.domain.GLJournalLine;

public interface GLJournalLineRepository extends JpaRepository<GLJournalLine, UUID> {

    List<GLJournalLine> findByJournalIdOrderBySequenceNo(UUID journalId);

    void deleteByJournalId(UUID journalId);
}
