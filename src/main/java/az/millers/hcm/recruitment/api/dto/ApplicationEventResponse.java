package az.millers.hcm.recruitment.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.recruitment.domain.ApplicationEvent;
import az.millers.hcm.recruitment.domain.ApplicationStage;
import az.millers.hcm.recruitment.domain.EventType;
import az.millers.hcm.recruitment.domain.Recommendation;

public record ApplicationEventResponse(
        UUID id,
        UUID applicationId,
        EventType eventType,
        ApplicationStage fromStage,
        ApplicationStage toStage,
        Integer rating,
        Recommendation recommendation,
        String comment,
        String actor,
        OffsetDateTime createdAt) {

    public static ApplicationEventResponse from(ApplicationEvent e) {
        return new ApplicationEventResponse(
                e.getId(), e.getApplicationId(),
                e.getEventType(), e.getFromStage(), e.getToStage(),
                e.getRating(), e.getRecommendation(), e.getComment(),
                e.getActor(), e.getCreatedAt());
    }
}
