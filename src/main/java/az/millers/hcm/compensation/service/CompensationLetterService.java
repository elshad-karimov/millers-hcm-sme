package az.millers.hcm.compensation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.compensation.domain.SalaryChangeRequest;
import az.millers.hcm.compensation.domain.SalaryChangeStatus;
import az.millers.hcm.letters.api.dto.LetterSubmitRequest;
import az.millers.hcm.letters.domain.LetterRequest;
import az.millers.hcm.letters.domain.LetterTemplate;
import az.millers.hcm.letters.repo.LetterTemplateRepository;
import az.millers.hcm.letters.service.LetterRequestService;

/**
 * M371 — Compensation letter generation service.
 * Reuses the LetterRequestService and seeded SALARY_INCREASE_LETTER template.
 */
@Service
public class CompensationLetterService {

    private static final Logger log = LoggerFactory.getLogger(CompensationLetterService.class);

    private static final String MODULE = "compensation";
    private static final String LETTER_TEMPLATE_CODE = "SALARY_INCREASE_LETTER";

    private final SalaryChangeRequestService salaryChangeService;
    private final LetterRequestService letterRequestService;
    private final LetterTemplateRepository letterTemplateRepo;
    private final AuditService audit;

    public CompensationLetterService(SalaryChangeRequestService salaryChangeService,
                                      LetterRequestService letterRequestService,
                                      LetterTemplateRepository letterTemplateRepo,
                                      AuditService audit) {
        this.salaryChangeService = salaryChangeService;
        this.letterRequestService = letterRequestService;
        this.letterTemplateRepo = letterTemplateRepo;
        this.audit = audit;
    }

    /**
     * Generate a salary increase letter for an APPLIED salary change request.
     * Reuses LetterRequestService.submit() with the seeded SALARY_INCREASE_LETTER template.
     *
     * @param salaryChangeRequestId the salary change request ID (must be APPLIED)
     * @return the LetterRequest with a download path via /api/letter-requests/{id}/pdf
     */
    @Transactional
    public LetterRequest generateSalaryIncreaseLetter(UUID salaryChangeRequestId) {
        SalaryChangeRequest scr = salaryChangeService.get(salaryChangeRequestId);

        if (scr.getStatus() != SalaryChangeStatus.APPLIED) {
            throw new BadRequestException(
                    "Salary change request must be APPLIED to generate a letter (current: " + scr.getStatus() + ")");
        }

        // Find the template (language-agnostic, will resolve to employee's language in LetterRequestService)
        LetterTemplate template = letterTemplateRepo.findByCodeAndLanguage(LETTER_TEMPLATE_CODE, "en")
                .orElseThrow(() -> new IllegalStateException(
                        "Letter template not found: " + LETTER_TEMPLATE_CODE));

        if (!template.isActive()) {
            throw new IllegalStateException("Letter template is not active: " + LETTER_TEMPLATE_CODE);
        }

        // Prepare custom fields for placeholders
        BigDecimal oldSalary = scr.getCurrentSalary() != null ? scr.getCurrentSalary() : BigDecimal.ZERO;
        BigDecimal newSalary = scr.getProposedSalary();
        BigDecimal increasePct = scr.getIncreasePct() != null ? scr.getIncreasePct() : BigDecimal.ZERO;

        DecimalFormat df = new DecimalFormat("#,##0.00");

        Map<String, Object> customFields = Map.of(
                "oldSalary", df.format(oldSalary),
                "newSalary", df.format(newSalary),
                "currency", scr.getCurrency(),
                "increasePct", df.format(increasePct),
                "effectiveDate", scr.getEffectiveDate().toString(),
                "reason", scr.getChangeReasonId() != null ? scr.getChangeReasonId().toString() : "Salary adjustment"
        );

        // Submit letter request via LetterRequestService (auto-issue for non-approval templates)
        LetterRequest letterRequest = letterRequestService.submit(new LetterSubmitRequest(
                scr.getEmployeeId(),
                template.getId(),
                "Salary increase notification for " + scr.getProposedSalary() + " " + scr.getCurrency(),
                customFields
        ));

        audit.record(MODULE, "CompensationLetter", letterRequest.getId().toString(),
                "GENERATED", null, Map.of(
                        "salaryChangeRequestId", salaryChangeRequestId.toString(),
                        "letterRequestNo", letterRequest.getRequestNo(),
                        "downloadPath", "/api/letter-requests/" + letterRequest.getId() + "/pdf"
                ));

        log.info("Generated salary increase letter {} for salary change request {}",
                letterRequest.getRequestNo(), salaryChangeRequestId);

        return letterRequest;
    }
}
