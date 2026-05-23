package az.millers.hcm.learning.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "course_competency", schema = "learning")
@IdClass(CourseCompetencyId.class)
@Getter
@Setter
@NoArgsConstructor
public class CourseCompetency {

    @Id
    @Column(name = "course_id")
    private UUID courseId;

    @Id
    @Column(name = "competency_id")
    private UUID competencyId;

    @Column(name = "awarded_level", nullable = false)
    private int awardedLevel = 3;
}
