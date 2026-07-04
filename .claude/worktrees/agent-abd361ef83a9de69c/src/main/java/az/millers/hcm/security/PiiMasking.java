package az.millers.hcm.security;

/**
 * Centralised PII masking helpers (M79 / P2-24).
 *
 * <p>Until now, masking was inlined per response DTO — masks for IBAN,
 * national IDs, account numbers, and certificate licence numbers each
 * lived next to the field they masked. That worked but drifted (e.g.
 * email and phone weren't masked at all because no DTO mapped them).
 *
 * <p>This class centralises the rules: pass the raw value, get back the
 * masked form. Callers decide <em>whether</em> to mask (the gate is
 * {@link PiiAccessRoles#callerCanSeePlaintextPii()}) and <em>which</em>
 * masking shape applies (per field type).
 */
public final class PiiMasking {

    private PiiMasking() {}

    /** Empty / null input → null so the JSON serialiser omits the field. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        int at = email.indexOf('@');
        if (at < 1) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return "*" + local.charAt(local.length() - 1) + domain;
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    /** Mask a phone number — keeps the last 4 digits, replaces the rest with •. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) return "•••• " + digits;
        String tail = digits.substring(digits.length() - 4);
        return "•••• •••• " + tail;
    }

    /** Mask a national / document ID — first 2 visible, then dots, last 2 visible. */
    public static String maskDocumentId(String id) {
        if (id == null || id.isBlank()) return null;
        if (id.length() <= 4) return "•••";
        return id.substring(0, 2) + "•••" + id.substring(id.length() - 2);
    }

    /** Mask an IBAN — first 4 + last 4 visible, dots in the middle. */
    public static String maskIban(String iban) {
        if (iban == null || iban.isBlank()) return null;
        String compact = iban.replaceAll("\\s", "");
        if (compact.length() <= 8) return "••••";
        return compact.substring(0, 4) + " •••• •••• •••• "
                + compact.substring(compact.length() - 4);
    }

    /** Mask a generic account / card number — only the last 4 stay visible. */
    public static String maskAccountNumber(String n) {
        if (n == null || n.isBlank()) return null;
        String digits = n.replaceAll("[^A-Za-z0-9]", "");
        if (digits.length() <= 4) return "••••";
        return "••••" + digits.substring(digits.length() - 4);
    }

    /** Convenience: return the masked or plaintext value based on the caller's role. */
    public static String emailFor(String email) {
        return PiiAccessRoles.callerCanSeePlaintextPii() ? email : maskEmail(email);
    }

    public static String phoneFor(String phone) {
        return PiiAccessRoles.callerCanSeePlaintextPii() ? phone : maskPhone(phone);
    }
}
