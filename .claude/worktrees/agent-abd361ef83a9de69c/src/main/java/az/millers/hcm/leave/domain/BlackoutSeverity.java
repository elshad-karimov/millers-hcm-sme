package az.millers.hcm.leave.domain;

/**
 * M123 — what happens when a leave request overlaps a {@link BlackoutWindow}.
 *
 * <ul>
 *   <li>{@code BLOCK} — hard reject. The submit call throws
 *       {@link az.millers.hcm.common.BadRequestException} with a message
 *       naming the window so the user sees a useful error.</li>
 *   <li>{@code REQUIRES_APPROVAL} — accept the request but flip the
 *       request's {@code blackoutFlag}. HR sees flagged requests in their
 *       approval inbox and can choose to override.</li>
 * </ul>
 */
public enum BlackoutSeverity {
    BLOCK,
    REQUIRES_APPROVAL
}
