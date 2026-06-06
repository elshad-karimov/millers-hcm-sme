package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * M130 — pure-static math for the goal cascade / OKR tree.
 *
 * <p>Centralises every judgment call the SPA + service make about the
 * parent/child relationship: cycle detection, depth, descendant lists,
 * weighted progress aggregation, alignment %.
 *
 * <p>Mockito-free so it's pinned by plain JUnit (Java 25 class-file
 * v69 isn't supported by Byte Buddy).
 */
public final class GoalTreeMath {

    /** Defensive cap on ancestor walk so a pre-existing cycle in data can't infinite-loop. */
    public static final int MAX_DEPTH = 32;

    private GoalTreeMath() {}

    /**
     * Pure data shape — only what the math needs.
     */
    public record GoalRef(
            UUID id,
            UUID parentId,
            UUID employeeId,
            UUID cycleId,
            BigDecimal weightPercent,
            BigDecimal progressPercent) {}

    /**
     * True iff assigning {@code proposedParentId} as the parent of
     * {@code childId} would close a cycle. Walks the ancestor chain
     * from the proposed parent up: if any ancestor is the child itself,
     * we'd create a loop.
     *
     * <p>{@code parentLookup} returns the parent's id for a given goal
     * id, or null if root/unknown.
     *
     * <p>Self-parent ({@code childId == proposedParentId}) is rejected
     * up-front.
     */
    public static boolean wouldCreateCycle(UUID childId,
                                            UUID proposedParentId,
                                            Function<UUID, UUID> parentLookup) {
        if (childId == null || proposedParentId == null) return false;
        if (childId.equals(proposedParentId)) return true;
        UUID cursor = proposedParentId;
        int hops = 0;
        Set<UUID> seen = new HashSet<>();
        while (cursor != null && hops < MAX_DEPTH) {
            if (cursor.equals(childId)) return true;
            // Pre-existing cycle in the ancestor chain means childId is
            // *not* in the chain (we'd have caught it). Walk terminates
            // — no NEW cycle would be created.
            if (!seen.add(cursor)) return false;
            cursor = parentLookup.apply(cursor);
            hops++;
        }
        return false;
    }

    /**
     * Depth of {@code goalId} — root = 0, child-of-root = 1, etc.
     * Capped at {@link #MAX_DEPTH} as a defence against pre-existing
     * cycles in data.
     */
    public static int depth(UUID goalId, Function<UUID, UUID> parentLookup) {
        if (goalId == null) return 0;
        int d = 0;
        UUID cursor = parentLookup.apply(goalId);
        Set<UUID> seen = new HashSet<>();
        seen.add(goalId);
        while (cursor != null && d < MAX_DEPTH) {
            if (!seen.add(cursor)) break;
            d++;
            cursor = parentLookup.apply(cursor);
        }
        return d;
    }

    /**
     * Returns all transitive descendants of {@code rootId} (NOT
     * including the root itself). DFS using a parent → children
     * adjacency view. Tolerates a pre-existing cycle by skipping
     * already-visited nodes.
     */
    public static List<UUID> descendantsOf(UUID rootId, Map<UUID, List<UUID>> childrenByParent) {
        List<UUID> out = new ArrayList<>();
        if (rootId == null || childrenByParent == null) return out;
        Set<UUID> seen = new HashSet<>();
        ArrayList<UUID> stack = new ArrayList<>();
        stack.add(rootId);
        seen.add(rootId);
        while (!stack.isEmpty()) {
            UUID cur = stack.remove(stack.size() - 1);
            List<UUID> kids = childrenByParent.get(cur);
            if (kids == null) continue;
            for (UUID k : kids) {
                if (seen.add(k)) {
                    out.add(k);
                    stack.add(k);
                }
            }
        }
        return out;
    }

    /**
     * Group goals into a parent → children adjacency map. Roots (no
     * parent) are not present as keys.
     */
    public static Map<UUID, List<UUID>> childrenByParent(Collection<GoalRef> goals) {
        Map<UUID, List<UUID>> out = new HashMap<>();
        if (goals == null) return out;
        for (GoalRef g : goals) {
            if (g.parentId() != null) {
                out.computeIfAbsent(g.parentId(), k -> new ArrayList<>()).add(g.id());
            }
        }
        return out;
    }

    /**
     * Look-up helper: {@code goalId → parentId}.
     */
    public static Map<UUID, UUID> parentByGoal(Collection<GoalRef> goals) {
        Map<UUID, UUID> out = new HashMap<>();
        if (goals == null) return out;
        for (GoalRef g : goals) {
            out.put(g.id(), g.parentId());
        }
        return out;
    }

    /**
     * Alignment % of {@code goalId}: the weighted average of its
     * descendants' {@code progressPercent}, with each descendant's own
     * {@code weightPercent} as the weight. If the goal has no
     * descendants, alignment falls back to the goal's own progress.
     *
     * <p>Result is clamped to {@code [0, 100]}, scale 2 HALF_UP.
     */
    public static BigDecimal alignmentPercent(UUID goalId,
                                                Map<UUID, GoalRef> byId,
                                                Map<UUID, List<UUID>> childrenByParent) {
        if (goalId == null || byId == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        GoalRef root = byId.get(goalId);
        if (root == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<UUID> descIds = descendantsOf(goalId, childrenByParent);
        if (descIds.isEmpty()) {
            return clamp(nz(root.progressPercent()));
        }
        BigDecimal weightSum = BigDecimal.ZERO;
        BigDecimal weightedProgress = BigDecimal.ZERO;
        for (UUID id : descIds) {
            GoalRef d = byId.get(id);
            if (d == null) continue;
            BigDecimal w = nz(d.weightPercent());
            BigDecimal p = nz(d.progressPercent());
            // Even a 0-weight descendant counts once at minimum weight 1
            // so a tree of all-zero-weight children doesn't divide by 0.
            if (w.signum() <= 0) w = BigDecimal.ONE;
            weightSum = weightSum.add(w);
            weightedProgress = weightedProgress.add(w.multiply(p));
        }
        if (weightSum.signum() <= 0) return clamp(nz(root.progressPercent()));
        return clamp(weightedProgress.divide(weightSum, 6, RoundingMode.HALF_UP));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal clamp(BigDecimal v) {
        if (v.signum() < 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cap = BigDecimal.valueOf(100);
        if (v.compareTo(cap) > 0) return cap.setScale(2, RoundingMode.HALF_UP);
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
