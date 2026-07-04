package az.millers.hcm.leave.domain;

/**
 * M123 — dimension a {@link BlackoutWindow} applies on.
 *
 * <p>{@code GLOBAL} affects every employee; {@code ORG_UNIT} narrows to a
 * specific unit and its descendants; {@code LEAVE_TYPE} narrows to a
 * single leave type code. The {@code chk_blackout_scope_id} DB constraint
 * keeps the unit/type ids in sync with the chosen scope.
 */
public enum BlackoutScope {
    GLOBAL,
    ORG_UNIT,
    LEAVE_TYPE
}
