package az.millers.hcm.selfservice.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.selfservice.domain.AnnouncementAudience;

public record CreateAnnouncementDto(
        String title,
        String body,
        LocalDate publishFrom,
        LocalDate publishTo,
        AnnouncementAudience audience,
        UUID audienceRef
) {}
