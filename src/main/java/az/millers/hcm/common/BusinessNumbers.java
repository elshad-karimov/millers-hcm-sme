package az.millers.hcm.common;

public final class BusinessNumbers {
    private BusinessNumbers() {}

    /** Format a business/reference number: prefix + '-' + zero-padded sequence. */
    public static String format(String prefix, int width, long seq) {
        return String.format("%s-%0" + width + "d", prefix, seq);
    }
}
