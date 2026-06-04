package az.millers.hcm.engagement.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.engagement.domain.CampaignStatus;
import az.millers.hcm.engagement.domain.SurveyCampaign;

public interface SurveyCampaignRepository extends JpaRepository<SurveyCampaign, UUID> {

    List<SurveyCampaign> findAllByOrderByCreatedAtDesc();

    List<SurveyCampaign> findByStatusOrderByCreatedAtDesc(CampaignStatus status);

    List<SurveyCampaign> findByTemplateIdOrderByCreatedAtDesc(UUID templateId);

    /**
     * Active campaigns whose date window covers {@code today}. Used by the
     * "what surveys can I take right now?" employee endpoint.
     */
    @Query("""
            select c from SurveyCampaign c
            where c.status = az.millers.hcm.engagement.domain.CampaignStatus.ACTIVE
              and c.opensOn <= :today
              and c.closesOn >= :today
            order by c.opensOn desc
            """)
    List<SurveyCampaign> findOpenOn(@Param("today") LocalDate today);
}
