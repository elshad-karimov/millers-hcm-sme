package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.Instructor;

public interface InstructorRepository extends JpaRepository<Instructor, UUID> {

    List<Instructor> findByTenantIdOrderByCreatedAtAsc(String tenantId);

    List<Instructor> findByTenantIdAndActiveTrueOrderByCreatedAtAsc(String tenantId);
}
