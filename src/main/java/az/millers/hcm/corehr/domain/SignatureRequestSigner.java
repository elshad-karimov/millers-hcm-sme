package az.millers.hcm.corehr.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "signature_request_signer", schema = "core_hr")
public class SignatureRequestSigner {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SignerStatus status = SignerStatus.PENDING;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "decline_reason", length = 1000)
    private String declineReason;

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public SignerStatus getStatus() {
        return status;
    }

    public void setStatus(SignerStatus status) {
        this.status = status;
    }

    public OffsetDateTime getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(OffsetDateTime signedAt) {
        this.signedAt = signedAt;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public enum SignerStatus {
        PENDING, SIGNED, DECLINED
    }
}
