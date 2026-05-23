package az.millers.hcm.learning.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.learning.domain.EnrolledVia;
import jakarta.validation.constraints.NotNull;

public record EnrollRequest(
        @NotNull UUID courseId,
        @NotNull UUID employeeId,
        EnrolledVia enrolledVia,
        LocalDate dueDate) {
}
