package az.millers.hcm.learning.domain;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "training_plan_item", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class TrainingPlanItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private TrainingPlan plan;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "position_id")
    private UUID positionId;

    @Column
    private String notes;

    @Column(name = "sort_order")
    private int sortOrder;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
