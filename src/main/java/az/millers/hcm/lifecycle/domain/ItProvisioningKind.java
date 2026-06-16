package az.millers.hcm.lifecycle.domain;

/**
 * Sub-classification of an IT / access provisioning request (M302 — Phase B.2).
 *
 * <p>Lets the IT queue + dashboards (Phase E) bucket requests the way the PRD
 * §3 IT widgets do: email accounts, system access, software licenses, access
 * cards, network/VPN. Request-tracking only — fulfilment records what was set
 * up ({@code provisionedRef}); no live account/mailbox creation.
 */
public enum ItProvisioningKind {
    EMAIL_ACCOUNT,
    SYSTEM_ACCESS,
    SOFTWARE_LICENSE,
    ACCESS_CARD,
    NETWORK_VPN,
    OTHER
}
