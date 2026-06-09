package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.staffing.domain.PositionProfileItem;
import az.millers.hcm.staffing.domain.ProfileItemType;
import az.millers.hcm.staffing.service.PositionProfileService.GrantPreview;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PositionProfileDtos {

    private PositionProfileDtos() {}

    public record ProfileItemRequest(
            @NotNull ProfileItemType itemType,
            @NotBlank @Size(max = 200) String label,
            BigDecimal valueAmount,
            @Size(min = 3, max = 3) String currency,
            boolean mandatory,
            @Size(max = 120) String referenceCode,
            @Size(max = 2000) String notes,
            int sortOrder) {

        public PositionProfileItem toEntity() {
            PositionProfileItem i = new PositionProfileItem();
            i.setItemType(itemType);
            i.setLabel(label);
            i.setValueAmount(valueAmount);
            i.setCurrency(currency);
            i.setMandatory(mandatory);
            i.setReferenceCode(referenceCode);
            i.setNotes(notes);
            i.setSortOrder(sortOrder);
            return i;
        }
    }

    public record ProfileItemResponse(
            UUID id, UUID positionId,
            ProfileItemType itemType, String label,
            BigDecimal valueAmount, String currency,
            boolean mandatory,
            String referenceCode, String notes,
            int sortOrder,
            OffsetDateTime createdAt, String createdBy,
            OffsetDateTime updatedAt, String updatedBy) {

        public static ProfileItemResponse from(PositionProfileItem i) {
            return new ProfileItemResponse(i.getId(), i.getPositionId(),
                    i.getItemType(), i.getLabel(),
                    i.getValueAmount(), i.getCurrency(),
                    i.isMandatory(),
                    i.getReferenceCode(), i.getNotes(),
                    i.getSortOrder(),
                    i.getCreatedAt(), i.getCreatedBy(),
                    i.getUpdatedAt(), i.getUpdatedBy());
        }
    }

    public record CloneFromRequest(@NotNull UUID sourcePositionId) {}

    public record GrantPreviewResponse(
            UUID profileItemId, UUID positionId, UUID employeeId,
            ProfileItemType itemType, String label,
            BigDecimal valueAmount, String currency,
            String referenceCode, String notes) {

        public static GrantPreviewResponse from(GrantPreview g) {
            return new GrantPreviewResponse(g.profileItemId(), g.positionId(), g.employeeId(),
                    g.itemType(), g.label(),
                    g.valueAmount(), g.currency(),
                    g.referenceCode(), g.notes());
        }
    }
}
