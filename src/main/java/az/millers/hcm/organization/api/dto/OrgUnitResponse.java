package az.millers.hcm.organization.api.dto;

import java.util.UUID;

import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.domain.OrgUnitType;

public record OrgUnitResponse(
        UUID id,
        UUID versionId,
        String code,
        String name,
        OrgUnitType unitType,
        UUID parentId,
        UUID headEmployeeId,
        int sortOrder,
        /** M141 — structured location FK. */
        UUID locationId,
        /** M81 — finance / facilities attributes. */
        String costCentreCode,
        String location,
        String contactEmail,
        String glAccount,
        Integer headcountBudget,
        boolean active) {

    public static OrgUnitResponse from(OrgUnit u) {
        return new OrgUnitResponse(
                u.getId(),
                u.getVersionId(),
                u.getCode(),
                u.getName(),
                u.getUnitType(),
                u.getParentId(),
                u.getHeadEmployeeId(),
                u.getSortOrder(),
                u.getLocationId(),
                u.getCostCentreCode(),
                u.getLocation(),
                u.getContactEmail(),
                u.getGlAccount(),
                u.getHeadcountBudget(),
                u.isActive());
    }
}
