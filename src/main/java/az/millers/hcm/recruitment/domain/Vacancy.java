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
@Table(name = "vacancy", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class Vacancy {

    @Id
    private UUID id;

    @Column(name = "vacancy_no", nullable = false, unique = true)
    private String vacancyNo;

    @Column(nullable = false)
    private String title;

    @Column(name = "position_id")
    private UUID positionId;

    private String department;
    private String location;

    @Column(nullable = false)
    private int openings;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String requirements;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "hiring_manager_id")
    private UUID hiringManagerId;

    @Column(name = "recruiter_id")
    private UUID recruiterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VacancyStatus status;

    @Column(name = "opening_date")
    private LocalDate openingDate;

    @Column(name = "closing_date")
    private LocalDate closingDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

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
