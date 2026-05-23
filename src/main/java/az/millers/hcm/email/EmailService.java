package az.millers.hcm.email;

import java.io.UnsupportedEncodingException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Thin wrapper over Spring's {@link JavaMailSender} for the reporting module.
 *
 * <p>Single public surface — {@link #sendReport} — so the rest of the
 * codebase doesn't have to think about MIME parts, multipart wrappers, or
 * address parsing. Failures are surfaced as {@link EmailDeliveryException}
 * carrying the underlying SMTP error so the calling service can record
 * {@code email_status = FAILED} on the {@code report_run} row without
 * crashing the surrounding transaction.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public EmailService(JavaMailSender mailSender,
                        @Value("${hcm.mail.from}") String fromAddress,
                        @Value("${hcm.mail.from-name}") String fromName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    /**
     * Send a report file as a MIME attachment.
     *
     * @param to            recipient list — at least one, valid RFC822 addresses
     * @param subject       subject line
     * @param htmlBody      HTML message body (also rendered as the plain alternative)
     * @param attachmentName file name shown to the recipient (e.g. {@code Headcount-2026-05.pdf})
     * @param bytes         file payload
     * @param contentType   MIME type ({@code application/pdf} or
     *                      {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet})
     * @throws EmailDeliveryException wrapping the SMTP failure
     */
    public void sendReport(List<String> to,
                           String subject,
                           String htmlBody,
                           String attachmentName,
                           byte[] bytes,
                           String contentType) throws EmailDeliveryException {
        if (to == null || to.isEmpty()) {
            throw new EmailDeliveryException("No recipients", null);
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            try {
                helper.setFrom(new InternetAddress(fromAddress, fromName));
            } catch (UnsupportedEncodingException ex) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(to.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            if (bytes != null && bytes.length > 0) {
                helper.addAttachment(attachmentName, new ByteArrayResource(bytes), contentType);
            }
            mailSender.send(message);
            log.info("Sent report '{}' to {} recipient(s) ({} bytes)",
                    attachmentName, to.size(), bytes != null ? bytes.length : 0);
        } catch (MailException | MessagingException ex) {
            log.warn("Email delivery failed for '{}' to {}: {}",
                    attachmentName, to, ex.getMessage());
            throw new EmailDeliveryException(ex.getMessage(), ex);
        }
    }
}
