package az.millers.hcm.learning.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * One row in the gap analysis report.
 *
 * <ul>
 *   <li>{@code gap > 0} — employee falls short of the requirement by {@code gap} levels</li>
 *   <li>{@code gap == 0} — employee meets the requirement exactly</li>
 *   <li>{@code gap < 0} — employee exceeds the requirement</li>
 * </ul>
 *
 * {@code currentProficiency} is {@code null} when the employee has no recorded
 * competency entry for this skill.
 */
public record GapItem(
        UUID competencyId,
        String competencyCode,
        String competencyName,
        String competencyCategory,
        int requiredProficiency,
        Integer currentProficiency,   // null = never assessed
        int gap,                      // required - current (null counted as 0 for current)
        List<CourseRecommendation> recommendedCourses
) {
    /** A published course that awards the competency at a useful level. */
    public record CourseRecommendation(
            UUID courseId,
            String courseCode,
            String courseTitle,
            int awardedLevel
    ) {}
}
