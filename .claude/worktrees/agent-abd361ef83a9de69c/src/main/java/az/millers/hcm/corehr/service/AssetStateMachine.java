package az.millers.hcm.corehr.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.AssetEventType;
import az.millers.hcm.corehr.domain.AssetStatus;

/**
 * M124 — pure-static transition table for {@link AssetStatus}. Captures
 * every legal status edge so we can reject illegal ones (e.g. reassign
 * a LOST asset) with a useful message instead of letting them slip
 * through the DB constraint with a generic 500.
 *
 * <p>Mockito-free so the contract gets pinned by plain JUnit.
 *
 * <pre>
 *   from         → to
 *   ─────────────────────────────────────────────
 *   ASSIGNED     → RETURNED, LOST, DAMAGED, WRITTEN_OFF
 *   RETURNED     → ASSIGNED  (reissue), WRITTEN_OFF
 *   DAMAGED      → WRITTEN_OFF
 *   LOST         → WRITTEN_OFF  (only after recovered as scrap)
 *   WRITTEN_OFF  → (terminal)
 * </pre>
 */
public final class AssetStateMachine {

    private static final Map<AssetStatus, Set<AssetStatus>> EDGES;
    static {
        EDGES = new EnumMap<>(AssetStatus.class);
        EDGES.put(AssetStatus.ASSIGNED, EnumSet.of(
                AssetStatus.RETURNED, AssetStatus.LOST,
                AssetStatus.DAMAGED, AssetStatus.WRITTEN_OFF));
        EDGES.put(AssetStatus.RETURNED, EnumSet.of(
                AssetStatus.ASSIGNED, AssetStatus.WRITTEN_OFF));
        EDGES.put(AssetStatus.DAMAGED, EnumSet.of(AssetStatus.WRITTEN_OFF));
        EDGES.put(AssetStatus.LOST, EnumSet.of(AssetStatus.WRITTEN_OFF));
        EDGES.put(AssetStatus.WRITTEN_OFF, EnumSet.noneOf(AssetStatus.class));
    }

    private AssetStateMachine() {}

    /** True iff {@code from → to} is a legal edge. */
    public static boolean canTransition(AssetStatus from, AssetStatus to) {
        if (from == null || to == null) return false;
        Set<AssetStatus> allowed = EDGES.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Throw {@link BadRequestException} naming both sides if the
     * transition is illegal. Caller already loaded the current state.
     */
    public static void requireTransition(AssetStatus from, AssetStatus to) {
        if (!canTransition(from, to)) {
            throw new BadRequestException(
                    "Illegal asset transition: " + from + " → " + to
                            + ". Allowed from " + from + ": " + EDGES.get(from));
        }
    }

    /** True iff the status is a closed-out terminal. */
    public static boolean isTerminal(AssetStatus s) {
        return s == AssetStatus.WRITTEN_OFF;
    }

    /** True iff the asset currently has a holder. */
    public static boolean isHeld(AssetStatus s) {
        return s == AssetStatus.ASSIGNED;
    }

    /**
     * Pick the event-type label for a (from → to) transition. Used by
     * {@link AssetEventService} when recording the move.
     */
    public static AssetEventType eventTypeFor(AssetStatus from, AssetStatus to) {
        if (from == null && to == AssetStatus.ASSIGNED) return AssetEventType.ASSIGN;
        if (from == AssetStatus.RETURNED && to == AssetStatus.ASSIGNED)
            return AssetEventType.REASSIGN;
        if (to == AssetStatus.RETURNED)    return AssetEventType.RETURN;
        if (to == AssetStatus.LOST)        return AssetEventType.MARK_LOST;
        if (to == AssetStatus.DAMAGED)     return AssetEventType.MARK_DAMAGED;
        if (to == AssetStatus.WRITTEN_OFF) return AssetEventType.WRITE_OFF;
        return AssetEventType.UPDATE_CONDITION;
    }
}
