package az.millers.hcm.learning.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.learning.domain.Certificate;

public record CertificateResponse(
        UUID id,
        String certificateNo,
        UUID enrollmentId,
        UUID courseId,
        UUID employeeId,
        OffsetDateTime issuedAt,
        LocalDate validUntil,
        BigDecimal scorePercent,
        boolean revoked,
        OffsetDateTime revokedAt,
        String revokedBy,
        String revokedReason) {

    public static CertificateResponse from(Certificate c) {
        return new CertificateResponse(
                c.getId(), c.getCertificateNo(), c.getEnrollmentId(),
                c.getCourseId(), c.getEmployeeId(), c.getIssuedAt(), c.getValidUntil(),
                c.getScorePercent(), c.isRevoked(), c.getRevokedAt(),
                c.getRevokedBy(), c.getRevokedReason());
    }
}
