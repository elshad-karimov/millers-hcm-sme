package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.performance.domain.MentoringRelationship;

public interface MentoringRelationshipRepository extends JpaRepository<MentoringRelationship, UUID> {

    @Query("""
        SELECT COUNT(*) FROM MentoringRelationship r
        WHERE r.mentorEmployeeId = :mentorId AND r.status = 'ACTIVE'
    """)
    long countActiveMenteesByMentor(@Param("mentorId") UUID mentorId);

    List<MentoringRelationship> findByMentorEmployeeIdOrMenteeEmployeeId(UUID mentorId, UUID menteeId);

    @Query("""
        SELECT r FROM MentoringRelationship r
        WHERE (r.menteeEmployeeId = :menteeId AND r.mentorEmployeeId = :mentorId)
          AND r.status IN ('REQUESTED', 'ACTIVE')
    """)
    List<MentoringRelationship> findActiveOrRequestedForPair(
            @Param("menteeId") UUID menteeId, @Param("mentorId") UUID mentorId);
}
