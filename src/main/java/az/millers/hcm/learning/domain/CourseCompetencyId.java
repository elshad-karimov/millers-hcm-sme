package az.millers.hcm.learning.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseCompetencyId implements Serializable {

    private UUID courseId;
    private UUID competencyId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CourseCompetencyId other)) return false;
        return Objects.equals(courseId, other.courseId)
                && Objects.equals(competencyId, other.competencyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courseId, competencyId);
    }
}
