package az.millers.hcm.organization.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrgUnitRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 64) String unitType,
        UUID parentId,
        UUID headEmployeeId,
        Integer sortOrder,
        /** M141 — structured location FK; supersedes the free-text location field. */
        UUID locationId,
        /** M142 — primary HRBP for this unit (soft FK to core_hr.employee). */
        UUID hrbpId,
        /** M81 — finance / facilities attributes. All optional. */
        @Size(max = 64) String costCentreCode,
        @Size(max = 200) String location,
        @Email @Size(max = 160) String contactEmail,
        @Size(max = 64) String glAccount,
        @Min(value = 0, message = "headcountBudget must be ≥ 0") Integer headcountBudget,
        /** M81 — defaults to true; null on update keeps existing value. */
        Boolean active,
        /** M144 — lifecycle state; null on create defaults to ACTIVE. */
        String lifecycleState,
        // ── M148 / §28 — Branch/Store enrichment ─────────────────────────
        @DecimalMin(value = "-90.0", message = "gpsLat must be ≥ -90")
        @DecimalMax(value = "90.0",  message = "gpsLat must be ≤ 90")
        BigDecimal gpsLat,
        @DecimalMin(value = "-180.0", message = "gpsLng must be ≥ -180")
        @DecimalMax(value = "180.0",  message = "gpsLng must be ≤ 180")
        BigDecimal gpsLng,
        @Size(max = 500) String operatingHours,
        @Size(max = 64)  String attendanceDeviceId,
        @Size(max = 120) String posSystemRef) {
}
