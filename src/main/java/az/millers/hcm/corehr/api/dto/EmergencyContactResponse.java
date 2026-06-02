package az.millers.hcm.corehr.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmergencyRelationship;
import az.millers.hcm.corehr.domain.EmployeeEmergencyContact;

public record EmergencyContactResponse(
        UUID id,
        UUID employeeId,
        String name,
        EmergencyRelationship relationship,
        String phone,
        String altPhone,
        String email,
        String address,
        boolean primary,
        int priorityOrder,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static EmergencyContactResponse from(EmployeeEmergencyContact e) {
        return new EmergencyContactResponse(
                e.getId(),
                e.getEmployeeId(),
                e.getName(),
                e.getRelationship(),
                e.getPhone(),
                e.getAltPhone(),
                e.getEmail(),
                e.getAddress(),
                e.isPrimary(),
                e.getPriorityOrder(),
                e.getCreatedAt(),
                e.getCreatedBy(),
                e.getUpdatedAt(),
                e.getUpdatedBy());
    }
}
