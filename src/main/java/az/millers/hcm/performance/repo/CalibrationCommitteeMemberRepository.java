package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.CalibrationCommitteeMember;

public interface CalibrationCommitteeMemberRepository
        extends JpaRepository<CalibrationCommitteeMember, UUID> {

    List<CalibrationCommitteeMember> findBySessionIdOrderByAddedAtAsc(UUID sessionId);

    boolean existsBySessionIdAndEmployeeId(UUID sessionId, UUID employeeId);

    boolean existsBySessionIdAndEmployeeIdAndMemberRoleIn(UUID sessionId, UUID employeeId,
                                                          List<String> roles);
}
