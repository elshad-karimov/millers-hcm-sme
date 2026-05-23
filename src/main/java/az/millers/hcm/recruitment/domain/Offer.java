package az.millers.hcm.recruitment.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "offer", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class Offer {

    @Id
    private UUID id;

    @Column(name = "offer_no", nullable = false, unique = true)
    private String offerNo;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    @Column(name = "proposed_salary", precision = 12, scale = 2)
    private BigDecimal proposedSalary;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "proposed_start_date")
    private LocalDate proposedStartDate;

    @Column(columnDefinition = "text")
    private String benefits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferStatus status;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "sent_by")
    private String sentBy;

    @Column(name = "response_at")
    private OffsetDateTime responseAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (currency == null) currency = "AZN";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
