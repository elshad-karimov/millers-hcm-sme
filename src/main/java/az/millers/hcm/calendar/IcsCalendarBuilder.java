package az.millers.hcm.calendar;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * M290 — minimal RFC 5545 (iCalendar) builder for a single timed event.
 *
 * <p>Reusable across modules (recruitment interviews now; leave / business
 * trips later) — give it the event facts and it returns a VCALENDAR string
 * suitable for emailing as a {@code text/calendar} attachment. Times are
 * emitted in UTC ({@code …Z}). Re-sends use a stable {@code uid} with an
 * incrementing {@code sequence}; {@link Method#CANCEL} produces a
 * cancellation that clients remove from the calendar.
 */
public final class IcsCalendarBuilder {

    private IcsCalendarBuilder() {}

    public enum Method { REQUEST, CANCEL }

    /** One invitee. */
    public record Attendee(String email, String name) {}

    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String PRODID = "-//Millers HCM//Recruitment//EN";

    public static String build(String uid,
                                int sequence,
                                Method method,
                                OffsetDateTime start,
                                int durationMinutes,
                                String summary,
                                String description,
                                String location,
                                String organizerEmail,
                                String organizerName,
                                List<Attendee> attendees) {
        OffsetDateTime startUtc = start.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime endUtc = startUtc.plusMinutes(Math.max(1, durationMinutes));
        OffsetDateTime stamp = OffsetDateTime.now(ZoneOffset.UTC);
        boolean cancel = method == Method.CANCEL;

        StringBuilder sb = new StringBuilder(512);
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:" + PRODID);
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:" + method.name());
        line(sb, "BEGIN:VEVENT");
        line(sb, "UID:" + uid);
        line(sb, "SEQUENCE:" + sequence);
        line(sb, "DTSTAMP:" + UTC.format(stamp));
        line(sb, "DTSTART:" + UTC.format(startUtc));
        line(sb, "DTEND:" + UTC.format(endUtc));
        line(sb, "SUMMARY:" + esc(summary));
        if (description != null && !description.isBlank()) {
            line(sb, "DESCRIPTION:" + esc(description));
        }
        if (location != null && !location.isBlank()) {
            line(sb, "LOCATION:" + esc(location));
        }
        if (organizerEmail != null && !organizerEmail.isBlank()) {
            String cn = organizerName == null || organizerName.isBlank() ? "" : "CN=" + esc(organizerName) + ":";
            line(sb, "ORGANIZER;" + cn + "MAILTO:" + organizerEmail);
        }
        if (attendees != null) {
            for (Attendee a : attendees) {
                if (a == null || a.email() == null || a.email().isBlank()) continue;
                String cn = a.name() == null || a.name().isBlank() ? "" : "CN=" + esc(a.name()) + ";";
                line(sb, "ATTENDEE;" + cn
                        + "ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:MAILTO:" + a.email());
            }
        }
        line(sb, "STATUS:" + (cancel ? "CANCELLED" : "CONFIRMED"));
        line(sb, "END:VEVENT");
        line(sb, "END:VCALENDAR");
        return sb.toString();
    }

    /** Append one content line with CRLF + RFC 5545 75-octet folding. */
    private static void line(StringBuilder sb, String content) {
        String s = content;
        int max = 73; // leave room for a leading space on continuation lines
        if (s.length() <= 75) {
            sb.append(s).append("\r\n");
            return;
        }
        int i = 0;
        sb.append(s, 0, 75).append("\r\n");
        i = 75;
        while (i < s.length()) {
            int end = Math.min(i + max, s.length());
            sb.append(' ').append(s, i, end).append("\r\n");
            i = end;
        }
    }

    /** RFC 5545 TEXT escaping: backslash, semicolon, comma, and newlines. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }
}
