package az.millers.hcm.learning.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Candidate-fit ranking for a position: employees ranked best-first by how well
 * their current competency levels meet the position's requirements.
 *
 * <p>{@code totalCandidates} is the number of active employees considered before
 * the ranking cap ({@link az.millers.hcm.learning.service.PositionFitService#FIT_RANK_CAP});
 * {@code ranked} may be shorter than that when the cap kicks in.
 */
public record PositionFitResponse(
        UUID positionId,
        int totalCandidates,
        List<EmployeeFitRow> ranked
) {
    /**
     * One ranked employee.
     *
     * <ul>
     *   <li>{@code fitScore} — 0..100 (0 = a mandatory requirement is a blocking gap)</li>
     *   <li>{@code blockers} — mandatory requirements the employee cannot meet</li>
     *   <li>{@code majorGaps} — requirements short by a material margin</li>
     * </ul>
     */
    public record EmployeeFitRow(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            int fitScore,
            int blockers,
            int majorGaps
    ) {}
}
