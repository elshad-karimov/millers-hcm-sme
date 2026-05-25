package az.millers.hcm.career.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ActivityRequest(
        String title,
        String activityType,
        UUID courseId,
        LocalDate dueDate,
        String notes
) {}
