package az.millers.hcm.recruitment.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.recruitment.domain.JobPosting;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

    @Query(value = "SELECT nextval('recruitment.posting_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<JobPosting> findByTenantIdAndVacancyIdOrderByCreatedAtDesc(String tenantId, UUID vacancyId);

    /**
     * M279/M281 — live postings for the career portal / internal job
     * board: PUBLISHED, deadline not passed, on the given channel.
     * Confidential vacancies are excluded by the JOIN predicate — a
     * confidential requisition must never surface on any board even
     * if someone publishes a posting by mistake.
     */
    @Query("""
            select p from JobPosting p, Vacancy v
            where v.id = p.vacancyId
              and p.tenantId = :tenantId
              and p.channel = :channel
              and p.status = az.millers.hcm.recruitment.domain.JobPosting.Status.PUBLISHED
              and (p.applicationDeadline is null or p.applicationDeadline >= :today)
              and v.confidential = false
            order by p.publishedAt desc
            """)
    List<JobPosting> findLiveByChannel(@Param("tenantId") String tenantId,
                                       @Param("channel") JobPosting.Channel channel,
                                       @Param("today") LocalDate today);

    /** M278 — daily expiry sweep: PUBLISHED postings past their deadline. */
    @Query("""
            select p from JobPosting p
            where p.tenantId = :tenantId
              and p.status = az.millers.hcm.recruitment.domain.JobPosting.Status.PUBLISHED
              and p.applicationDeadline is not null
              and p.applicationDeadline < :today
            """)
    List<JobPosting> findExpired(@Param("tenantId") String tenantId,
                                 @Param("today") LocalDate today);
}
