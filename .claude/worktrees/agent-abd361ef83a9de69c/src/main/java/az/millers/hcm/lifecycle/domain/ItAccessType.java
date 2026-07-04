package az.millers.hcm.lifecycle.domain;

public enum ItAccessType {
    ERP_ACCESS("ERP / HCM Access"),
    EMAIL_ACCOUNT("Email Account"),
    VPN("VPN Access"),
    ACTIVE_DIRECTORY("Active Directory / SSO"),
    POS_ACCESS("POS System"),
    CRM_ACCESS("CRM System"),
    CLOUD_SYSTEMS("Cloud Systems"),
    GIT_ACCESS("Git / Source Code"),
    BIOMETRIC("Biometric / Access Card"),
    MOBILE_APP("Mobile App Access"),
    MFA("MFA / Tokens"),
    OTHER("Other");

    private final String label;

    ItAccessType(String label) { this.label = label; }

    public String getLabel() { return label; }
}
