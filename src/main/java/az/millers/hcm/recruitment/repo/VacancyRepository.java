package az.millers.hcm.recruitment.repo;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.domain.VacancyStatus;

public interface VacancyRepository extends JpaRepository<Vacancy, UUID> {

    @Query(value = "SELECT nextval('recruitment.vacancy_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Page<Vacancy> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Vacancy> findByStatusOrderByCreatedAtDesc(VacancyStatus status, Pageable pageable);

    /**
     * M277 — list with confidential filtering (Recruitment PRD §41).
     * A confidential requisition is visible when the caller is
     * unrestricted (HR_ADMIN / SYSTEM_ADMIN) or is its named recruiter
     * / hiring manager. {@code employeeId} may be null (user with no
     * linked employee row) — null never matches the named columns.
     */
    @Query("""
            select v from Vacancy v
            where (v.confidential = false
                   or :unrestricted = true
                   or v.recruiterId = :employeeId
                   or v.hiringManagerId = :employeeId)
            order by v.createdAt desc
            """)
    Page<Vacancy> findAllVisible(@org.springframework.data.repository.query.Param("unrestricted") boolean unrestricted,
                                 @org.springframework.data.repository.query.Param("employeeId") UUID employeeId,
                                 Pageable pageable);

    /** M277 — status-filtered variant of {@link #findAllVisible}. */
    @Query("""
            select v from Vacancy v
            where v.status = :status
              and (v.confidential = false
                   or :unrestricted = true
                   or v.recruiterId = :employeeId
                   or v.hiringManagerId = :employeeId)
            order by v.createdAt desc
            """)
    Page<Vacancy> findByStatusVisible(@org.springframework.data.repository.query.Param("status") VacancyStatus status,
                                      @org.springframework.data.repository.query.Param("unrestricted") boolean unrestricted,
                                      @org.springframework.data.repository.query.Param("employeeId") UUID employeeId,
                                      Pageable pageable);

    /**
     * Sum of {@code openings} across vacancies in the given status — used by
     * {@code PositionHeadcountService} to count outstanding requisitions
     * against a position's approved headcount (M109).
     */
    @Query("select coalesce(sum(v.openings), 0) from Vacancy v "
            + "where v.positionId = :positionId and v.status = :status")
    int sumOpeningsByPositionAndStatus(@org.springframework.data.repository.query.Param("positionId") UUID positionId,
                                       @org.springframework.data.repository.query.Param("status") VacancyStatus status);

    /**
     * M274 — multi-status variant. The headcount gate needs OPEN +
     * PUBLISHED counted together now that PUBLISHED is a distinct
     * "accepting candidates" state.
     */
    @Query("select coalesce(sum(v.openings), 0) from Vacancy v "
            + "where v.positionId = :positionId and v.status in :statuses")
    int sumOpeningsByPositionAndStatusIn(@org.springframework.data.repository.query.Param("positionId") UUID positionId,
                                         @org.springframework.data.repository.query.Param("statuses") java.util.Collection<VacancyStatus> statuses);

    /** M272 — all vacancies on a position (for impact analysis). */
    java.util.List<Vacancy> findByPositionIdOrderByCreatedAtDesc(UUID positionId);
}
