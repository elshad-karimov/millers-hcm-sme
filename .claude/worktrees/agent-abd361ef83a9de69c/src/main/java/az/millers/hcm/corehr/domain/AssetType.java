package az.millers.hcm.corehr.domain;

/**
 * Company-property categories tracked by {@link EmployeeAsset} (M72 / P2-09).
 *
 * <p>Reasonably exhaustive list — common workplace assets that show up in
 * the offboarding clearance checklist. {@code OTHER} catches anything not
 * listed; if a category becomes common, promote it to its own enum value
 * and migrate the data.
 */
public enum AssetType {
    LAPTOP,
    MOBILE_PHONE,
    SIM_CARD,
    VEHICLE,
    ACCESS_CARD,
    UNIFORM,
    TOOLS,
    EQUIPMENT,
    LOCKER,
    FUEL_CARD,
    CREDIT_CARD,
    TABLET,
    HEADSET,
    OTHER
}
