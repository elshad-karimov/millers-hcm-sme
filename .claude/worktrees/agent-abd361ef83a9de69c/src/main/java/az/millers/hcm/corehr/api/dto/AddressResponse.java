package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.AddressType;
import az.millers.hcm.corehr.domain.EmployeeAddress;

public record AddressResponse(
        UUID id,
        UUID employeeId,
        AddressType addressType,
        String addressLine1,
        String addressLine2,
        String city,
        String district,
        String country,
        String postalCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean current,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static AddressResponse from(EmployeeAddress a) {
        return new AddressResponse(
                a.getId(),
                a.getEmployeeId(),
                a.getAddressType(),
                a.getAddressLine1(),
                a.getAddressLine2(),
                a.getCity(),
                a.getDistrict(),
                a.getCountry(),
                a.getPostalCode(),
                a.getEffectiveFrom(),
                a.getEffectiveTo(),
                a.isCurrent(),
                a.getCreatedAt(),
                a.getCreatedBy(),
                a.getUpdatedAt(),
                a.getUpdatedBy());
    }
}
