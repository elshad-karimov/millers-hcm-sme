package az.millers.hcm.selfservice.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.selfservice.domain.Announcement;
import az.millers.hcm.selfservice.domain.AnnouncementAudience;

public record AnnouncementDto(
        UUID id,
        String title,
        String body,
        LocalDate publishFrom,
        LocalDate publishTo,
        AnnouncementAudience audience,
        UUID audienceRef,
        Boolean active
) {
    public static AnnouncementDto from(Announcement entity) {
        return new AnnouncementDto(
                entity.getId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getPublishFrom(),
                entity.getPublishTo(),
                entity.getAudience(),
                entity.getAudienceRef(),
                entity.getActive()
        );
    }
}
