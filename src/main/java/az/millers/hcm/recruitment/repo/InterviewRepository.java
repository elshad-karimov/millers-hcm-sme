package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.Interview;
import az.millers.hcm.recruitment.domain.InterviewStatus;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('recruitment.interview_no_seq')", nativeQuery = true)
    long nextInterviewNoSequence();

    List<Interview> findByApplicationIdOrderByScheduledAtDesc(UUID applicationId);

    List<Interview> findByInterviewerEmployeeIdOrderByScheduledAtDesc(UUID interviewerEmployeeId);

    List<Interview> findByStatusOrderByScheduledAtAsc(InterviewStatus status);

    @Query("""
            select i from Interview i
            where i.interviewerEmployeeId = :empId
              and i.status = :status
            order by i.scheduledAt asc
            """)
    List<Interview> findByInterviewerAndStatus(UUID empId, InterviewStatus status);
}
