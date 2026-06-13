package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.OfferRevision;

public interface OfferRevisionRepository extends JpaRepository<OfferRevision, UUID> {

    List<OfferRevision> findByOfferIdOrderByRevisionNoDesc(UUID offerId);

    int countByOfferId(UUID offerId);
}
