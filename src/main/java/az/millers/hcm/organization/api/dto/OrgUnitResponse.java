package az.millers.hcm.organization.api.dto;

import java.util.UUID;

import az.millers.hcm.organization.domain.OrgUnit;

public record OrgUnitResponse(
        UUID id,
        UUID versionId,
        String code,
        String name,
        String unitType,
        UUID parentId,
        UUID headEmployeeId,
        int sortOrder,
        /** M141 — structured location FK. */
        UUID locationId,
        /** M142 — primary HRBP employee id. */
        UUID hrbpId,
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
                u.getHrbpId(),
                u.getCostCentreCode(),
                u.getLocation(),
                u.getContactEmail(),
                u.getGlAccount(),
                u.getHeadcountBudget(),
                u.isActive());
    }
}
