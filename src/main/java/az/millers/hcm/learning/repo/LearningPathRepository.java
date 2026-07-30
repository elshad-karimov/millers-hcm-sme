package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.learning.domain.LearningPath;

public interface LearningPathRepository extends JpaRepository<LearningPath, UUID> {

    List<LearningPath> findByActiveOrderByPathNoAsc(boolean active);

    @Query(value = "SELECT config.next_tenant_seq('learning.learning_path_no_seq')", nativeQuery = true)
    long nextNoSequence();
}
