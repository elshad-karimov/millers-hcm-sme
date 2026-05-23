package az.millers.hcm.organization.api.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.organization.domain.OrgUnitType;

public record OrgTreeNode(
        UUID id,
        String code,
        String name,
        OrgUnitType unitType,
        UUID parentId,
        UUID headEmployeeId,
        int sortOrder,
        List<OrgTreeNode> children) {

    public static OrgTreeNode leaf(OrgUnitResponse u) {
        return new OrgTreeNode(u.id(), u.code(), u.name(), u.unitType(),
                u.parentId(), u.headEmployeeId(), u.sortOrder(), new ArrayList<>());
    }
}
