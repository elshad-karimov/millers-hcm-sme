package az.millers.hcm.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.notifications.domain.NotificationChannel;
import az.millers.hcm.notifications.domain.NotificationPreference;
import az.millers.hcm.notifications.service.NotificationPreferenceService.CategoryRow;
import az.millers.hcm.notifications.service.NotificationPreferenceService.PreferenceGrid;

/**
 * Pure-math pinning for the M115 preference resolver.
 *
 * <p>The bits worth pinning: the opt-IN default (every category × channel
 * defaults to enabled), the override behaviour (a stored row beats the
 * default), and the TRANSACTIONAL invariant (cannot be muted, ever).
 * Getting any of these wrong silently breaks either delivery (false
 * negatives) or compliance (sending what a user opted out of).
 */
class NotificationPreferenceServiceTest {

    // ── defaultEnabled() ────────────────────────────────────────────────

    @Test
    void defaultEnabledTrueForEveryMutableCategoryAndChannel() {
        for (NotificationCategory cat : NotificationCategory.values()) {
            for (NotificationChannel ch : NotificationChannel.values()) {
                assertThat(NotificationPreferenceService.defaultEnabled(cat, ch))
                        .as("%s × %s default", cat, ch)
                        .isTrue();
            }
        }
    }

    @Test
    void defaultEnabledTrueForNullCategory() {
        // Defensive — null category should never block delivery.
        assertThat(NotificationPreferenceService.defaultEnabled(
                null, NotificationChannel.EMAIL)).isTrue();
    }

    // ── resolveEnabled() ────────────────────────────────────────────────

    @Test
    void resolveDefaultsToTrueWhenNoStoredRow() {
        assertThat(NotificationPreferenceService.resolveEnabled(
                NotificationCategory.EXPIRY_ALERT,
                NotificationChannel.EMAIL,
                List.of())).isTrue();
    }

    @Test
    void resolveReturnsStoredEnabledValue() {
        NotificationPreference pref = pref(NotificationCategory.EXPIRY_ALERT,
                NotificationChannel.EMAIL, false);
        assertThat(NotificationPreferenceService.resolveEnabled(
                NotificationCategory.EXPIRY_ALERT,
                NotificationChannel.EMAIL,
                List.of(pref))).isFalse();
    }

    @Test
    void resolveOnlyMatchesExactCategoryAndChannel() {
        NotificationPreference pref = pref(NotificationCategory.LEARNING_REMINDER,
                NotificationChannel.PUSH, false);
        // Same category, different channel → not a match.
        assertThat(NotificationPreferenceService.resolveEnabled(
                NotificationCategory.LEARNING_REMINDER,
                NotificationChannel.EMAIL,
                List.of(pref))).isTrue();
        // Same channel, different category → not a match.
        assertThat(NotificationPreferenceService.resolveEnabled(
                NotificationCategory.EXPIRY_ALERT,
                NotificationChannel.PUSH,
                List.of(pref))).isTrue();
    }

    @Test
    void resolvePicksFirstMatchOfMultipleStoredRows() {
        // Two rows for the same (cat, ch) shouldn't happen (unique index),
        // but if a sloppy bulk import slipped them in we mustn't crash.
        NotificationPreference a = pref(NotificationCategory.EXPIRY_ALERT,
                NotificationChannel.EMAIL, false);
        NotificationPreference b = pref(NotificationCategory.EXPIRY_ALERT,
                NotificationChannel.EMAIL, true);
        boolean resolved = NotificationPreferenceService.resolveEnabled(
                NotificationCategory.EXPIRY_ALERT,
                NotificationChannel.EMAIL,
                List.of(a, b));
        // First match wins (deterministic).
        assertThat(resolved).isFalse();
    }

    // ── buildGrid() ─────────────────────────────────────────────────────

    @Test
    void gridIncludesEveryCategoryEvenWithoutStoredRows() {
        PreferenceGrid grid = NotificationPreferenceService.buildGrid("alice", List.of());
        assertThat(grid.username()).isEqualTo("alice");
        assertThat(grid.categories())
                .extracting(CategoryRow::category)
                .containsExactlyInAnyOrder(NotificationCategory.values());
    }

    @Test
    void gridIncludesEveryChannelPerCategory() {
        PreferenceGrid grid = NotificationPreferenceService.buildGrid("alice", List.of());
        for (CategoryRow row : grid.categories()) {
            assertThat(row.channels())
                    .as("category=%s", row.category())
                    .containsOnlyKeys(NotificationChannel.values());
        }
    }

    @Test
    void gridDefaultsAllCellsEnabled() {
        PreferenceGrid grid = NotificationPreferenceService.buildGrid("alice", List.of());
        for (CategoryRow row : grid.categories()) {
            for (Boolean enabled : row.channels().values()) {
                assertThat(enabled).as("category=%s", row.category()).isTrue();
            }
        }
    }

    @Test
    void gridReflectsStoredOptOut() {
        NotificationPreference stored = pref(NotificationCategory.STALE_POOL_REMINDER,
                NotificationChannel.EMAIL, false);
        PreferenceGrid grid = NotificationPreferenceService.buildGrid("alice", List.of(stored));
        CategoryRow row = grid.categories().stream()
                .filter(r -> r.category() == NotificationCategory.STALE_POOL_REMINDER)
                .findFirst().orElseThrow();
        assertThat(row.channels().get(NotificationChannel.EMAIL)).isFalse();
        // Other channels in the same category stay at the default.
        assertThat(row.channels().get(NotificationChannel.IN_APP)).isTrue();
        assertThat(row.channels().get(NotificationChannel.PUSH)).isTrue();
    }

    @Test
    void gridFlagsTransactionalAsImmutable() {
        PreferenceGrid grid = NotificationPreferenceService.buildGrid("alice", List.of());
        CategoryRow transactional = grid.categories().stream()
                .filter(r -> r.category() == NotificationCategory.TRANSACTIONAL)
                .findFirst().orElseThrow();
        assertThat(transactional.mutable()).isFalse();
    }

    @Test
    void gridFlagsAllNonTransactionalAsMutable() {
        PreferenceGrid grid = NotificationPreferenceService.buildGrid("alice", List.of());
        for (CategoryRow row : grid.categories()) {
            if (row.category() == NotificationCategory.TRANSACTIONAL) continue;
            assertThat(row.mutable()).as("category=%s", row.category()).isTrue();
        }
    }

    // ── NotificationCategory.isMutable() invariant ─────────────────────

    @Test
    void onlyTransactionalIsImmutable() {
        for (NotificationCategory cat : NotificationCategory.values()) {
            boolean shouldBeMutable = cat != NotificationCategory.TRANSACTIONAL;
            assertThat(cat.isMutable()).as("%s mutable", cat).isEqualTo(shouldBeMutable);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static NotificationPreference pref(NotificationCategory cat,
                                                 NotificationChannel ch,
                                                 boolean enabled) {
        NotificationPreference p = new NotificationPreference();
        p.setCategory(cat);
        p.setChannel(ch);
        p.setEnabled(enabled);
        return p;
    }
}
