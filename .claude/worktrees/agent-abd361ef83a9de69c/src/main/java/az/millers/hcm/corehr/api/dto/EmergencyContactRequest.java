package az.millers.hcm.corehr.api.dto;

import az.millers.hcm.corehr.domain.EmergencyRelationship;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmergencyContactRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull EmergencyRelationship relationship,
        @NotBlank @Size(max = 40) String phone,
        @Size(max = 40) String altPhone,
        @Email @Size(max = 160) String email,
        @Size(max = 2000) String address,
        Boolean primary,
        Integer priorityOrder) {
}
