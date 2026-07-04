package az.millers.hcm.selfservice.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import az.millers.hcm.selfservice.domain.Announcement;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    List<Announcement> findByTenantIdAndActiveTrueOrderByPublishFromDesc(String tenantId);
}
