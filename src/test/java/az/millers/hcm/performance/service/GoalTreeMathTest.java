package az.millers.hcm.performance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.performance.service.GoalTreeMath.GoalRef;

/**
 * M130 — pins the OKR-tree math: cycle detection, depth, descendant
 * walks, and weighted alignment %. These are the only places production
 * code makes judgment calls about the parent/child graph, so they're
 * worth nailing down.
 */
class GoalTreeMathTest {

    private static GoalRef goal(UUID id, UUID parent, int weight, int progress) {
        return new GoalRef(id, parent, UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.valueOf(weight), BigDecimal.valueOf(progress));
    }

    // ── wouldCreateCycle ──────────────────────────────────────────────────

    @Test
    void wouldCreateCycleRejectsSelfParent() {
        UUID a = UUID.randomUUID();
        assertThat(GoalTreeMath.wouldCreateCycle(a, a, id -> null)).isTrue();
    }

    @Test
    void wouldCreateCycleFalseForUnrelatedNodes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // b is a root.
        assertThat(GoalTreeMath.wouldCreateCycle(a, b, id -> null)).isFalse();
    }

    @Test
    void wouldCreateCycleTrueWhenAncestorIsChild() {
        // chain: top → mid → bottom. Now ask "can top's parent be bottom?"
        UUID top = UUID.randomUUID();
        UUID mid = UUID.randomUUID();
        UUID bottom = UUID.randomUUID();
        Map<UUID, UUID> parents = Map.of(mid, top, bottom, mid);
        // Setting bottom as top's parent would put top below itself.
        assertThat(GoalTreeMath.wouldCreateCycle(top, bottom, parents::get)).isTrue();
    }

    @Test
    void wouldCreateCycleSurvivesPreExistingCycle() {
        // Pathological data: a → b → a. Walking should terminate, not loop.
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, UUID> parents = Map.of(a, b, b, a);
        UUID c = UUID.randomUUID();
        // Asking would-be-cycle for c (a fresh node) should NOT hang.
        assertThat(GoalTreeMath.wouldCreateCycle(c, a, parents::get)).isFalse();
    }

    @Test
    void wouldCreateCycleHandlesNulls() {
        UUID a = UUID.randomUUID();
        assertThat(GoalTreeMath.wouldCreateCycle(null, a, id -> null)).isFalse();
        assertThat(GoalTreeMath.wouldCreateCycle(a, null, id -> null)).isFalse();
    }

    // ── depth ──────────────────────────────────────────────────────────────

    @Test
    void depthZeroForRoot() {
        UUID r = UUID.randomUUID();
        assertThat(GoalTreeMath.depth(r, id -> null)).isZero();
    }

    @Test
    void depthCountsAncestorHops() {
        UUID r = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID gc = UUID.randomUUID();
        Map<UUID, UUID> parents = Map.of(c, r, gc, c);
        assertThat(GoalTreeMath.depth(r, parents::get)).isZero();
        assertThat(GoalTreeMath.depth(c, parents::get)).isEqualTo(1);
        assertThat(GoalTreeMath.depth(gc, parents::get)).isEqualTo(2);
    }

    @Test
    void depthCapsAtMaxOnCycle() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, UUID> parents = Map.of(a, b, b, a);
        // Terminates without throwing.
        int d = GoalTreeMath.depth(a, parents::get);
        assertThat(d).isLessThanOrEqualTo(GoalTreeMath.MAX_DEPTH);
    }

    // ── descendantsOf ──────────────────────────────────────────────────────

    @Test
    void descendantsOfWalksWholeTree() {
        UUID root = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        UUID gc1 = UUID.randomUUID();
        Map<UUID, List<UUID>> kids = new HashMap<>();
        kids.put(root, List.of(c1, c2));
        kids.put(c1, List.of(gc1));
        List<UUID> out = GoalTreeMath.descendantsOf(root, kids);
        assertThat(out).containsExactlyInAnyOrder(c1, c2, gc1);
    }

    @Test
    void descendantsOfEmptyForLeaf() {
        UUID leaf = UUID.randomUUID();
        assertThat(GoalTreeMath.descendantsOf(leaf, Map.of())).isEmpty();
    }

    // ── childrenByParent / parentByGoal ───────────────────────────────────

    @Test
    void childrenByParentGroupsCorrectly() {
        UUID root = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        var goals = List.of(
                goal(root, null, 50, 0),
                goal(c1, root, 30, 50),
                goal(c2, root, 70, 25));
        var byParent = GoalTreeMath.childrenByParent(goals);
        assertThat(byParent.get(root)).containsExactlyInAnyOrder(c1, c2);
        assertThat(byParent).doesNotContainKey(c1);
    }

    // ── alignmentPercent ───────────────────────────────────────────────────

    @Test
    void alignmentFallsBackToOwnProgressWhenLeaf() {
        UUID leaf = UUID.randomUUID();
        var byId = Map.of(leaf, goal(leaf, null, 40, 65));
        BigDecimal a = GoalTreeMath.alignmentPercent(leaf, byId, Map.of());
        assertThat(a).isEqualByComparingTo("65.00");
    }

    @Test
    void alignmentIsWeightedAverageOfDescendants() {
        UUID root = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        // c1: weight 30, progress 100. c2: weight 70, progress 50.
        // Weighted avg = (30·100 + 70·50) / 100 = (3000 + 3500) / 100 = 65.
        var byId = Map.of(
                root, goal(root, null,  0,   0),
                c1,   goal(c1,   root, 30, 100),
                c2,   goal(c2,   root, 70,  50));
        var byParent = Map.of(root, List.of(c1, c2));
        BigDecimal a = GoalTreeMath.alignmentPercent(root, byId, byParent);
        assertThat(a).isEqualByComparingTo("65.00");
    }

    @Test
    void alignmentZeroWeightDescendantsCountEqually() {
        UUID root = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        // Both zero-weight; should split 50/50 → avg of 80 and 20 = 50.
        var byId = Map.of(
                root, goal(root, null, 0,   0),
                c1,   goal(c1,   root, 0,  80),
                c2,   goal(c2,   root, 0,  20));
        var byParent = Map.of(root, List.of(c1, c2));
        BigDecimal a = GoalTreeMath.alignmentPercent(root, byId, byParent);
        assertThat(a).isEqualByComparingTo("50.00");
    }

    @Test
    void alignmentClampsToHundred() {
        UUID root = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        // Pathological progress 200 → clamps to 100.
        var byId = Map.of(
                root, goal(root, null, 0,   0),
                c1,   goal(c1,   root, 50, 200));
        var byParent = Map.of(root, List.of(c1));
        BigDecimal a = GoalTreeMath.alignmentPercent(root, byId, byParent);
        assertThat(a).isEqualByComparingTo("100.00");
    }

    @Test
    void alignmentZeroForUnknownGoal() {
        BigDecimal a = GoalTreeMath.alignmentPercent(UUID.randomUUID(), Map.of(), Map.of());
        assertThat(a).isEqualByComparingTo("0.00");
    }
}
