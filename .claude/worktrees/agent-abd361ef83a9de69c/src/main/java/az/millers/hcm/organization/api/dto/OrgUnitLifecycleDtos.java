package az.millers.hcm.organization.api.dto;

import java.time.LocalDate;

/** M144 — wire DTOs for org-unit lifecycle transitions (§26). */
public final class OrgUnitLifecycleDtos {

    private OrgUnitLifecycleDtos() {}

    /** Body for announce-closure and close endpoints. */
    public record ClosureRequest(
            /** Target effective close date. */
            LocalDate effectiveDate,
            String reason) {}

    /** Body for reopen and open endpoints. */
    public record ReopenRequest(String reason) {}
}
