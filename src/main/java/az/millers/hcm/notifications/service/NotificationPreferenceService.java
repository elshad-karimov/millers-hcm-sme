package az.millers.hcm.notifications.service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.notifications.domain.NotificationChannel;
import az.millers.hcm.notifications.domain.NotificationPreference;
import az.millers.hcm.notifications.repo.NotificationPreferenceRepository;

/**
 * Per-user notification opt-out (M115).
 *
 * <p>The contract is intentionally minimal:
 * <ul>
 *   <li>{@link #shouldSend(String, NotificationCategory, NotificationChannel)}
 *       is consulted by senders before delivery. Returns {@code true} when
 *       the user has no preference row OR has a row with
 *       {@code enabled = true}. {@link NotificationCategory#TRANSACTIONAL}
 *       always returns {@code true} regardless of stored preferences —
 *       security and urgent operational notifications can't be muted.</li>
 *   <li>{@link #upsert} is the only write — set/clear an opt-out for one
 *       (category, channel) pair. Audited.</li>
 *   <li>{@link #listFor} returns the full grid (one entry per mutable
 *       category × channel) for the settings UI, with stored opt-outs
 *       merged onto the default-true grid.</li>
 * </ul>
 *
 * <p>Pure helpers ({@link #defaultEnabled}, {@link #resolveEnabled},
 * {@link #buildGrid}) are package-private so the unit suite can pin them.
 */
@Service
public class NotificationPreferenceService {

    private static final String MODULE = "NOTIFICATIONS";
    private static final String ENTITY = "NotificationPreference";

    private final NotificationPreferenceRepository repo;
    private final AuditService audit;

    public NotificationPreferenceService(NotificationPreferenceRepository repo,
                                          AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    /** A single row in the settings UI grid — one cell per channel for a category. */
    public record PreferenceCell(
            NotificationCategory category,
            NotificationChannel channel,
            boolean enabled,
            boolean mutable) {
    }

    /** Per-category row. */
    public record CategoryRow(
            NotificationCategory category,
            String displayName,
            String description,
            boolean mutable,
            Map<NotificationChannel, Boolean> channels) {
    }

    /** Top-level payload returned by {@code GET /me/notification-preferences}. */
    public record PreferenceGrid(
            String username,
            List<CategoryRow> categories) {
    }

    // ─── Public surface ─────────────────────────────────────────────────

    /**
     * The hot path. Called by every sender immediately before pushing a
     * notification through a channel. Returns {@code true} when delivery
     * should proceed.
     */
    @Transactional(readOnly = true)
    public boolean shouldSend(String username, NotificationCategory category,
                              NotificationChannel channel) {
        if (category == null || channel == null) return true;
        if (!category.isMutable()) return true;
        if (username == null || username.isBlank()) return true;
        return repo.findByUsernameAndCategoryAndChannel(username, category, channel)
                .map(NotificationPreference::isEnabled)
                .orElse(defaultEnabled(category, channel));
    }

    @Transactional(readOnly = true)
    public PreferenceGrid listFor(String username) {
        if (username == null || username.isBlank()) {
            throw new BadRequestException("username is required");
        }
        List<NotificationPreference> stored = repo.findByUsername(username);
        return buildGrid(username, stored);
    }

    @Transactional
    public NotificationPreference upsert(String username, NotificationCategory category,
                                          NotificationChannel channel, boolean enabled) {
        if (username == null || username.isBlank()) {
            throw new BadRequestException("username is required");
        }
        if (category == null || channel == null) {
            throw new BadRequestException("category and channel are required");
        }
        if (!category.isMutable()) {
            throw new BadRequestException(
                    "Category " + category + " cannot be muted (it is TRANSACTIONAL)");
        }
        NotificationPreference row = repo
                .findByUsernameAndCategoryAndChannel(username, category, channel)
                .orElseGet(NotificationPreference::new);
        Boolean before = row.getId() == null ? null : row.isEnabled();
        row.setUsername(username);
        row.setCategory(category);
        row.setChannel(channel);
        row.setEnabled(enabled);
        NotificationPreference saved = repo.save(row);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                before == null ? "CREATE" : "UPDATE",
                before == null ? null : Map.of(
                        "username", username,
                        "category", category.name(),
                        "channel", channel.name(),
                        "enabled", before),
                Map.of(
                        "username", username,
                        "category", category.name(),
                        "channel", channel.name(),
                        "enabled", enabled));
        return saved;
    }

    // ─── Pure helpers (package-private for testing) ─────────────────────

    /**
     * The opt-IN default. Currently {@code true} for every (category, channel)
     * pair — the M115 contract. Kept as a function so a future "opt out by
     * default" flag for a specific channel (e.g. PUSH) is a one-liner here.
     */
    static boolean defaultEnabled(NotificationCategory category, NotificationChannel channel) {
        if (category == null || !category.isMutable()) return true;
        return true;
    }

    /**
     * Apply a list of stored preferences onto the default grid. Stored
     * rows that disagree with the default override it; unknown
     * (category, channel) combinations are ignored (defensive — they
     * could appear after rolling back a code change that removed an enum
     * value while preferences remain in the DB).
     */
    static boolean resolveEnabled(NotificationCategory category,
                                    NotificationChannel channel,
                                    List<NotificationPreference> stored) {
        for (NotificationPreference p : stored) {
            if (p.getCategory() == category && p.getChannel() == channel) {
                return p.isEnabled();
            }
        }
        return defaultEnabled(category, channel);
    }

    /** Build the complete grid (every category × every channel). */
    static PreferenceGrid buildGrid(String username, List<NotificationPreference> stored) {
        List<CategoryRow> rows = new java.util.ArrayList<>();
        for (NotificationCategory cat : NotificationCategory.values()) {
            Map<NotificationChannel, Boolean> channelMap = new EnumMap<>(NotificationChannel.class);
            for (NotificationChannel ch : NotificationChannel.values()) {
                channelMap.put(ch, resolveEnabled(cat, ch, stored));
            }
            rows.add(new CategoryRow(
                    cat, cat.displayName(), cat.description(), cat.isMutable(),
                    channelMap));
        }
        return new PreferenceGrid(username, rows);
    }

    @SuppressWarnings("unused")
    private static HashMap<Object, Object> empty() { return new HashMap<>(); }
}
