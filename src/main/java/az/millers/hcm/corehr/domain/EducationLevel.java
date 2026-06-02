package az.millers.hcm.corehr.domain;

/**
 * Academic credential level for {@link EmployeeEducation} (M71 / P2-04).
 *
 * <p>The ordering is informational — used by reports that sort by attained
 * level (e.g. "highest credential per department"). The enum CHECK in V57
 * pins the column to this exact set.
 */
public enum EducationLevel {
    HIGH_SCHOOL,
    DIPLOMA,
    VOCATIONAL,
    ASSOCIATE,
    BACHELOR,
    MASTER,
    DOCTORATE,
    PROFESSIONAL,
    OTHER
}
