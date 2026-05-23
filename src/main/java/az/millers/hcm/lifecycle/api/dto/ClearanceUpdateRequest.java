package az.millers.hcm.lifecycle.api.dto;

public record ClearanceUpdateRequest(
        Boolean clearanceIt,
        Boolean clearanceHr,
        Boolean clearanceFinance,
        Boolean clearanceAssets) {
}
