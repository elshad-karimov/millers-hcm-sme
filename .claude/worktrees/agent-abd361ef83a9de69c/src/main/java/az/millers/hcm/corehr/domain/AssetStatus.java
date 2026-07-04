package az.millers.hcm.corehr.domain;

/**
 * Lifecycle of an {@link EmployeeAsset}.
 *
 * <pre>
 *   ASSIGNED    — handed to the employee; no returned_at yet
 *   RETURNED    — handed back; returned_at + condition_at_return recorded
 *   LOST        — terminal; employee couldn't return it
 *   DAMAGED     — terminal; returned but unfit for re-issue
 *   WRITTEN_OFF — terminal; cost absorbed (end-of-life, theft, etc.)
 * </pre>
 *
 * <p>The DB CHECK in V58 enforces "non-ASSIGNED rows must have a returned_at"
 * so the column never disagrees with the status.
 */
public enum AssetStatus {
    ASSIGNED, RETURNED, LOST, DAMAGED, WRITTEN_OFF
}
