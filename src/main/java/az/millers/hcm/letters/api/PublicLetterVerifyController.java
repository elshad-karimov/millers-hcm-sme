package az.millers.hcm.letters.api;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.letters.domain.LetterRequest;
import az.millers.hcm.letters.service.LetterRequestService;

/**
 * M139 — public letter verification surface.
 *
 * <p>The 32-char token printed in the QR code IS the credential —
 * SecurityConfig permits anonymous access at {@code /api/public/letters/verify/**}.
 * This controller deliberately returns only non-PII fields so the
 * endpoint stays safe when third-party verifiers (banks, embassies,
 * landlords) hit it from arbitrary networks.
 */
@RestController
@RequestMapping("/api/public/letters/verify")
public class PublicLetterVerifyController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final LetterRequestService service;

    public PublicLetterVerifyController(LetterRequestService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    public VerifyResponse verify(@PathVariable String token) {
        LetterRequest r = service.verifyByToken(token);
        return new VerifyResponse(
                r.getRequestNo(),
                r.getStatus().name(),
                r.getIssuedAt() == null ? null : r.getIssuedAt().toLocalDate().format(ISO),
                r.getSignedBy(),
                r.getLanguage());
    }

    /**
     * Wire payload returned by the verify endpoint. Intentionally
     * excludes employee id / name / national id / body — anything PII.
     */
    public record VerifyResponse(
            String requestNo,
            String status,
            String issuedDate,
            String signedBy,
            String language) {
        /** Compile-time reference so unused imports don't trigger warnings. */
        @SuppressWarnings("unused")
        private static final Class<?> UNUSED_REF = OffsetDateTime.class;
    }
}
