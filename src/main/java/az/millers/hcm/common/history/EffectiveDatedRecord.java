package az.millers.hcm.common.history;

import java.time.LocalDate;

/**
 * Common contract for any row that uses the {@code (effective_from, effective_to)}
 * pattern to represent a slice of an entity's history (M62).
 *
 * <p>The convention across the codebase:
 * <ul>
 *   <li>{@code effective_from} is always non-null — the day the slice begins</li>
 *   <li>{@code effective_to} is null on the currently-open row; otherwise the
 *       last day this slice was active</li>
 *   <li>at most one row per logical key has {@code effective_to IS NULL} at any time</li>
 * </ul>
 *
 * <p>The {@link #closeOn(LocalDate)} default method centralises the "close the
 * previous slice when a new one starts" mutation that was previously duplicated
 * in {@code CompensationService.upsert} and was about to be re-duplicated in
 * the new {@code EmployeeStatusHistory} / {@code EmployeeEmploymentHistory}
 * services. Entities implement this interface and services call
 * {@code currentRow.closeOn(newRow.getEffectiveFrom())} — no copy-paste, one
 * place to fix off-by-one bugs.
 *
 * <p>This is a behaviour-only interface; entities still own their own JPA
 * mapping, audit columns, and PrePersist logic. No {@code @MappedSuperclass}
 * is introduced (the codebase deliberately avoids JPA inheritance).
 */
public interface EffectiveDatedRecord {

    LocalDate getEffectiveFrom();

    LocalDate getEffectiveTo();

    void setEffectiveTo(LocalDate to);

    /**
     * Closes this row so its successor (starting at {@code newEffectiveFrom})
     * can be inserted without temporal overlap.
     *
     * <p>Sets {@code effectiveTo = newEffectiveFrom - 1 day}.
     *
     * @throws IllegalArgumentException if {@code newEffectiveFrom} is null or
     *         not strictly after this row's {@code effectiveFrom}.
     */
    default void closeOn(LocalDate newEffectiveFrom) {
        if (newEffectiveFrom == null) {
            throw new IllegalArgumentException("newEffectiveFrom is required");
        }
        LocalDate ownStart = getEffectiveFrom();
        if (ownStart != null && !newEffectiveFrom.isAfter(ownStart)) {
            throw new IllegalArgumentException(
                    "newEffectiveFrom (" + newEffectiveFrom
                            + ") must be strictly after this row's effectiveFrom ("
                            + ownStart + ")");
        }
        setEffectiveTo(newEffectiveFrom.minusDays(1));
    }

    /** Convenience: is this row currently open-ended (no effective_to set)? */
    default boolean isCurrent() {
        return getEffectiveTo() == null;
    }
}
