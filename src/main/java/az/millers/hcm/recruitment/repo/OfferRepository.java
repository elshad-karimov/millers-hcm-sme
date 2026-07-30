package az.millers.hcm.recruitment.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.Offer;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('recruitment.offer_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Optional<Offer> findByApplicationId(UUID applicationId);
}
