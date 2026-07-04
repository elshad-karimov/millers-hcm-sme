package az.millers.hcm.career.api.dto;

import java.time.LocalDate;
import java.util.List;

public record IdpRequest(
        String targetRole,
        LocalDate targetDate,
        List<SkillGapRequest> skillGaps,
        List<ActivityRequest> activities
) {}
