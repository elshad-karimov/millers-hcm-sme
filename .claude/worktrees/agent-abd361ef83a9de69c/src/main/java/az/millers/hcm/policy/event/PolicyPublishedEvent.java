package az.millers.hcm.policy.event;

import java.util.UUID;

/**
 * Published when a {@link az.millers.hcm.policy.domain.PolicyDocument} transitions
 * from DRAFT to PUBLISHED and {@code requiresAck} is {@code true}.
 *
 * <p>Consumed by {@link az.millers.hcm.policy.service.PolicyAckNotificationListener}
 * to broadcast acknowledgement requests to all ACTIVE employees (M192).
 */
public record PolicyPublishedEvent(
        UUID policyId,
        String code,
        String title,
        int version) {
}
