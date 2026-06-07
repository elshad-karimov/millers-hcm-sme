package az.millers.hcm.learning.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * M136 — body of {@code POST /api/learning/competencies/employees/{employeeCompetencyId}/endorse}.
 *
 * <p>A manager / mentor confirms the proficiency level for an existing
 * employee_competency. {@code endorsedLevel} usually equals the
 * record's current proficiency but can differ when the endorser
 * disagrees — in that case the service treats the endorsement as
 * authoritative and updates the proficiency to match.
 *
 * <p>The endorser's identity is taken from the auth context — not
 * supplied in the request — so an employee can't endorse on behalf of
 * someone else.
 */
public record EndorsementRequest(
        @NotNull @Min(1) @Max(5) Integer endorsedLevel,
        @Size(max = 2000) String note) {
}
