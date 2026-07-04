package az.millers.hcm.selfservice.api.dto;

import az.millers.hcm.selfservice.domain.ServiceRequestCategory;
import az.millers.hcm.selfservice.domain.ServiceRequestPriority;

public record SubmitServiceRequestDto(
        ServiceRequestCategory category,
        ServiceRequestPriority priority,
        String subject,
        String description
) {}
