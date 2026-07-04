package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.ApplicationStage;
import az.millers.hcm.recruitment.domain.StageSla;

public interface StageSlaRepository extends JpaRepository<StageSla, UUID> {

    List<StageSla> findAllByOrderByStageAsc();

    Optional<StageSla> findByStage(ApplicationStage stage);
}
