package az.millers.hcm.recruitment.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.letters.repo.LetterTemplateRepository;
import az.millers.hcm.letters.service.LetterPdfRenderer;
import az.millers.hcm.letters.service.LetterRenderer;
import az.millers.hcm.recruitment.domain.Application;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.Offer;
import az.millers.hcm.recruitment.domain.OfferStatus;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;
import az.millers.hcm.recruitment.repo.OfferRepository;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M283 — Recruitment PRD §31: offer letter generation.
 *
 * <p>Renders an APPROVED-or-later offer as a PDF using the M77/M139
 * letter engine: the OFFER_STANDARD template (bilingual, seeded in
 * V149) + the M283 raw-context renderer overload + the shared
 * letterhead/signature PDF layout. Rendered on demand — no storage;
 * the offer chain IS the source of truth, so a re-render after a
 * counteroffer always reflects current terms.
 */
@Service
public class OfferLetterService {

    private static final String TEMPLATE_CODE = "OFFER_STANDARD";
    /** PRD §29 "offer expiry": validity window shown on the letter. */
    private static final int VALIDITY_DAYS = 14;

    private final OfferRepository offers;
    private final ApplicationRepository applications;
    private final CandidateRepository candidates;
    private final VacancyRepository vacancies;
    private final LetterTemplateRepository templates;
    private final LetterRenderer renderer;
    private final LetterPdfRenderer pdfRenderer;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public OfferLetterService(OfferRepository offers,
                               ApplicationRepository applications,
                               CandidateRepository candidates,
                               VacancyRepository vacancies,
                               LetterTemplateRepository templates,
                               LetterRenderer renderer,
                               LetterPdfRenderer pdfRenderer,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.offers = offers;
        this.applications = applications;
        this.candidates = candidates;
        this.vacancies = vacancies;
        this.templates = templates;
        this.renderer = renderer;
        this.pdfRenderer = pdfRenderer;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    public record OfferLetter(String filename, byte[] pdf) {}

    @Transactional(readOnly = true)
    public OfferLetter render(UUID offerId, String language) {
        Offer o = offers.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + offerId));
        // PRD §70 "Offer cannot be sent before approval" — and a letter
        // IS the send artifact. DRAFT / PENDING_APPROVAL render nothing.
        if (o.getStatus() == OfferStatus.DRAFT
                || o.getStatus() == OfferStatus.PENDING_APPROVAL) {
            throw new BadRequestException(
                    "Offer letter is available after approval (current: " + o.getStatus() + ")");
        }

        Application app = applications.findById(o.getApplicationId())
                .orElseThrow(() -> new BadRequestException("Application missing"));
        Candidate c = candidates.findById(app.getCandidateId())
                .orElseThrow(() -> new BadRequestException("Candidate missing"));
        Vacancy v = vacancies.findById(app.getVacancyId())
                .orElseThrow(() -> new BadRequestException("Vacancy missing"));

        String lang = language == null || language.isBlank() ? "az" : language.toLowerCase();
        var template = templates.findByCodeAndLanguage(TEMPLATE_CODE, lang)
                // Fall back to any language rather than failing — a
                // missing translation shouldn't block an offer.
                .or(() -> templates.findByCode(TEMPLATE_CODE))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offer letter template not found: " + TEMPLATE_CODE));

        String body = renderer.render(template.getBody(), buildContext(o, c, v));
        byte[] pdf = pdfRenderer.renderDocument(
                template.getName(),
                o.getOfferNo(),
                o.getSentAt() != null ? o.getSentAt().toLocalDate() : LocalDate.now(),
                template.getLanguage(),
                body,
                "Human Resources Department",
                LocalDate.now(),
                null); // no public verify endpoint for offers (yet)

        audit.record("RECRUITMENT", "Offer", offerId.toString(), "LETTER_RENDERED",
                null,
                Map.of("template", TEMPLATE_CODE,
                        "language", template.getLanguage(),
                        "renderedBy", currentRequest.username()));

        return new OfferLetter(
                o.getOfferNo() + "-" + template.getLanguage() + ".pdf", pdf);
    }

    private Map<String, Object> buildContext(Offer o, Candidate c, Vacancy v) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("candidate.fullName",
                (nz(c.getFirstName()) + " " + nz(c.getLastName())).trim());
        ctx.put("offer.no", o.getOfferNo());
        ctx.put("offer.salary",
                o.getProposedSalary() == null ? "—" : o.getProposedSalary().toPlainString());
        ctx.put("offer.currency", nz(o.getCurrency()));
        ctx.put("offer.startDate",
                o.getProposedStartDate() == null ? "—" : o.getProposedStartDate().toString());
        ctx.put("offer.benefits",
                o.getBenefits() == null || o.getBenefits().isBlank()
                        ? "as per company policy" : o.getBenefits());
        // Validity anchored on SENT when known, else "today + window".
        LocalDate anchor = o.getSentAt() != null ? o.getSentAt().toLocalDate() : LocalDate.now();
        ctx.put("offer.validUntil", anchor.plusDays(VALIDITY_DAYS).toString());
        ctx.put("vacancy.title", nz(v.getTitle()));
        ctx.put("vacancy.department", v.getDepartment() == null ? "—" : v.getDepartment());
        ctx.put("vacancy.employmentType",
                v.getEmploymentType() == null ? "FULL TIME"
                        : v.getEmploymentType().replace('_', ' '));
        // Pre-built suffix so templates read naturally with or without
        // a location ("…team, based in Baku." vs "…team.").
        ctx.put("vacancy.locationSuffix",
                v.getLocation() == null || v.getLocation().isBlank()
                        ? "" : ", based in " + v.getLocation());
        return ctx;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
