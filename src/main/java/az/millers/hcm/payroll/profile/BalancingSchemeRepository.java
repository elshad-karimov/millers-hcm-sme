package az.millers.hcm.payroll.profile;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BalancingSchemeRepository extends JpaRepository<BalancingScheme, UUID> {

    Optional<BalancingScheme> findByCode(String code);
}
