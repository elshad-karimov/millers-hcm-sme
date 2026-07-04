package az.millers.hcm.learning.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.learning.domain.LearningPath;
import az.millers.hcm.learning.domain.LearningPathCourse;

public record LearningPathResponse(
        UUID id,
        String pathNo,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        List<StepView> steps) {

    public record StepView(
            UUID id,
            UUID courseId,
            int stepOrder,
            boolean requiredToAdvance) {

        public static StepView from(LearningPathCourse s) {
            return new StepView(s.getId(), s.getCourseId(), s.getStepOrder(), s.isRequiredToAdvance());
        }
    }

    public static LearningPathResponse from(LearningPath p, List<LearningPathCourse> steps) {
        return new LearningPathResponse(
                p.getId(), p.getPathNo(), p.getName(), p.getDescription(),
                p.isActive(), p.getCreatedAt(),
                steps.stream().map(StepView::from).toList());
    }
}
