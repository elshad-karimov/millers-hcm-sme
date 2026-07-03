package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.RatingScaleValue;

public interface RatingScaleValueRepository extends JpaRepository<RatingScaleValue, UUID> {

    List<RatingScaleValue> findByScaleIdOrderByValueOrderAsc(UUID scaleId);

    void deleteByScaleId(UUID scaleId);
}
