package az.millers.hcm.recruitment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.AgencyDtos.AgencyRequest;
import az.millers.hcm.recruitment.api.dto.AgencyDtos.AgencyResponse;
import az.millers.hcm.recruitment.api.dto.AgencyDtos.InvoiceResponse;
import az.millers.hcm.recruitment.api.dto.AgencyDtos.InvoiceUpdate;
import az.millers.hcm.recruitment.api.dto.AgencyDtos.SubmissionRequest;
import az.millers.hcm.recruitment.api.dto.AgencyDtos.SubmissionResponse;
import az.millers.hcm.recruitment.domain.Agency;
import az.millers.hcm.recruitment.domain.AgencyFeeType;
import az.millers.hcm.recruitment.domain.AgencyInvoice;
import az.millers.hcm.recruitment.domain.AgencySubmission;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.InvoiceStatus;
import az.millers.hcm.recruitment.domain.SubmissionStatus;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.repo.AgencyInvoiceRepository;
import az.millers.hcm.recruitment.repo.AgencyRepository;
import az.millers.hcm.recruitment.repo.AgencySubmissionRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

/**
 * M296 — Recruitment PRD Phase F: agency master, candidate ownership
 * window, and placement invoicing.
 *
 * <p>An agency submits a candidate (ACTIVE) and owns them until
 * {@code ownershipUntil}; no other agency may submit the same candidate
 * while that holds. When an owned candidate is hired the submission flips
 * to HIRED and a DRAFT placement invoice is raised for the agency fee.
 */
@Service
public class AgencyService {

    private static final Logger log = LoggerFactory.getLogger(AgencyService.class);
    private static final String MODULE = "RECRUITMENT";

    private final AgencyRepository agencies;
    private final AgencySubmissionRepository submissions;
    private final AgencyInvoiceRepository invoices;
    private final CandidateRepository candidates;
    private final VacancyRepository vacancies;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public AgencyService(AgencyRepository agencies,
                          AgencySubmissionRepository submissions,
                          AgencyInvoiceRepository invoices,
                          CandidateRepository candidates,
                          VacancyRepository vacancies,
                          AuditService audit,
                          CurrentRequest currentRequest) {
        this.agencies = agencies;
        this.submissions = submissions;
        this.invoices = invoices;
        this.candidates = candidates;
        this.vacancies = vacancies;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Agency master ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AgencyResponse> listAgencies(boolean activeOnly) {
        var list = activeOnly ? agencies.findByActiveTrueOrderByNameAsc()
                : agencies.findAllByOrderByNameAsc();
        return list.stream().map(AgencyResponse::from).toList();
    }

    @Transactional
    public AgencyResponse createAgency(AgencyRequest req) {
        Agency a = new Agency();
        a.setAgencyNo(BusinessNumbers.format("AG", 5, agencies.nextNoSequence()));
        applyAgency(a, req);
        a.setCreatedBy(currentRequest.username());
        a.setUpdatedBy(currentRequest.username());
        Agency saved = agencies.save(a);
        audit.record(MODULE, "Agency", saved.getId().toString(), "CREATE", null,
                Map.of("agencyNo", saved.getAgencyNo(), "name", saved.getName()));
        return AgencyResponse.from(saved);
    }

    @Transactional
    public AgencyResponse updateAgency(UUID id, AgencyRequest req) {
        Agency a = agencies.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agency not found: " + id));
        applyAgency(a, req);
        a.setUpdatedBy(currentRequest.username());
        return AgencyResponse.from(agencies.save(a));
    }

    private void applyAgency(Agency a, AgencyRequest req) {
        a.setName(req.name());
        a.setContactName(req.contactName());
        a.setContactEmail(req.contactEmail());
        a.setContactPhone(req.contactPhone());
        a.setFeeType(req.feeType());
        a.setFeePercent(req.feePercent());
        a.setFeeFixed(req.feeFixed());
        if (req.ownershipDays() != null) a.setOwnershipDays(req.ownershipDays());
        if (req.currency() != null) a.setCurrency(req.currency());
        if (req.active() != null) a.setActive(req.active());
        a.setNotes(req.notes());

        if (req.feeType() == AgencyFeeType.PERCENT_SALARY
                && (req.feePercent() == null || req.feePercent().signum() <= 0)) {
            throw new BadRequestException("A percentage-fee agency needs a fee percent");
        }
        if (req.feeType() == AgencyFeeType.FIXED
                && (req.feeFixed() == null || req.feeFixed().signum() < 0)) {
            throw new BadRequestException("A fixed-fee agency needs a fixed fee amount");
        }
    }

    // ── Submissions + ownership ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SubmissionResponse> listSubmissions(SubmissionStatus status) {
        var rows = status == null ? submissions.findAllByOrderByCreatedAtDesc()
                : submissions.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(this::enrich).toList();
    }

    @Transactional
    public SubmissionResponse createSubmission(SubmissionRequest req) {
        Agency agency = agencies.findById(req.agencyId())
                .orElseThrow(() -> new BadRequestException("Agency not found: " + req.agencyId()));
        Candidate cand = candidates.findById(req.candidateId())
                .orElseThrow(() -> new BadRequestException("Candidate not found: " + req.candidateId()));
        if (req.vacancyId() != null && !vacancies.existsById(req.vacancyId())) {
            throw new BadRequestException("Vacancy not found: " + req.vacancyId());
        }

        // Ownership guard: an unexpired ACTIVE submission blocks a new one.
        OffsetDateTime now = OffsetDateTime.now();
        var existing = submissions.findFirstByCandidateIdAndStatusOrderBySubmittedAtDesc(
                cand.getId(), SubmissionStatus.ACTIVE);
        if (existing.isPresent()) {
            AgencySubmission ex = existing.get();
            if (ex.getOwnershipUntil().isAfter(now)) {
                Agency owner = agencies.findById(ex.getAgencyId()).orElse(null);
                throw new BadRequestException("Candidate is under ownership of "
                        + (owner == null ? "another agency" : owner.getName())
                        + " until " + ex.getOwnershipUntil().toLocalDate());
            }
            // Lapsed — expire it so the new submission can take ownership.
            ex.setStatus(SubmissionStatus.EXPIRED);
            submissions.save(ex);
        }

        AgencySubmission s = new AgencySubmission();
        s.setSubmissionNo(BusinessNumbers.format("SUB", 5, submissions.nextNoSequence()));
        s.setAgencyId(agency.getId());
        s.setCandidateId(cand.getId());
        s.setVacancyId(req.vacancyId());
        s.setStatus(SubmissionStatus.ACTIVE);
        s.setSubmittedAt(now);
        s.setOwnershipUntil(now.plusDays(agency.getOwnershipDays()));
        s.setNotes(req.notes());
        s.setCreatedBy(currentRequest.username());
        s.setUpdatedBy(currentRequest.username());
        AgencySubmission saved = submissions.save(s);
        audit.record(MODULE, "AgencySubmission", saved.getId().toString(), "CREATE", null,
                Map.of("agency", agency.getName(), "candidateId", cand.getId().toString(),
                        "ownershipUntil", saved.getOwnershipUntil().toLocalDate().toString()));
        return enrich(saved);
    }

    @Transactional
    public SubmissionResponse withdrawSubmission(UUID id) {
        AgencySubmission s = submissions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + id));
        if (s.getStatus() == SubmissionStatus.HIRED) {
            throw new BadRequestException("A hired submission cannot be withdrawn");
        }
        s.setStatus(SubmissionStatus.WITHDRAWN);
        s.setUpdatedBy(currentRequest.username());
        AgencySubmission saved = submissions.save(s);
        audit.record(MODULE, "AgencySubmission", id.toString(), "WITHDRAW", null, null);
        return enrich(saved);
    }

    /**
     * Hook from {@link ApplicationService#hire}: if the hired candidate is
     * under an active agency ownership window, flip the submission to HIRED
     * and raise a DRAFT placement invoice. Best-effort — never blocks the
     * hire.
     */
    @Transactional
    public void onCandidateHired(UUID candidateId, UUID employeeId, BigDecimal proposedSalary) {
        var opt = submissions.findFirstByCandidateIdAndStatusOrderBySubmittedAtDesc(
                candidateId, SubmissionStatus.ACTIVE);
        if (opt.isEmpty()) return;
        AgencySubmission s = opt.get();
        if (s.getOwnershipUntil().isBefore(OffsetDateTime.now())) return; // window lapsed
        Agency agency = agencies.findById(s.getAgencyId()).orElse(null);
        if (agency == null) return;

        s.setStatus(SubmissionStatus.HIRED);
        s.setHireEmployeeId(employeeId);
        submissions.save(s);

        if (!invoices.existsBySubmissionId(s.getId())) {
            BigDecimal fee = computeFee(agency, proposedSalary);
            AgencyInvoice inv = new AgencyInvoice();
            inv.setInvoiceNo(BusinessNumbers.format("INV", 5, invoices.nextNoSequence()));
            inv.setAgencyId(agency.getId());
            inv.setSubmissionId(s.getId());
            inv.setCandidateId(candidateId);
            inv.setHireEmployeeId(employeeId);
            inv.setFeeAmount(fee);
            inv.setCurrency(agency.getCurrency());
            inv.setStatus(InvoiceStatus.DRAFT);
            invoices.save(inv);
            audit.record(MODULE, "AgencyInvoice", inv.getId().toString(), "CREATE", null,
                    Map.of("agency", agency.getName(), "submissionNo", s.getSubmissionNo(),
                            "fee", fee.toPlainString()));
            log.info("Agency {} placement: submission {} → HIRED, invoice {} for {} {}",
                    agency.getName(), s.getSubmissionNo(), inv.getInvoiceNo(), fee, agency.getCurrency());
        }
    }

    private BigDecimal computeFee(Agency agency, BigDecimal proposedSalary) {
        if (agency.getFeeType() == AgencyFeeType.FIXED) {
            return agency.getFeeFixed() == null ? BigDecimal.ZERO : agency.getFeeFixed();
        }
        // PERCENT_SALARY — % of annualised salary (12 × monthly proposed).
        if (proposedSalary == null || agency.getFeePercent() == null) return BigDecimal.ZERO;
        return proposedSalary.multiply(BigDecimal.valueOf(12))
                .multiply(agency.getFeePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Expire ACTIVE submissions whose ownership window has lapsed. */
    @Transactional
    public int expireOverdue() {
        List<AgencySubmission> lapsed = submissions.findByStatusAndOwnershipUntilLessThan(
                SubmissionStatus.ACTIVE, OffsetDateTime.now());
        for (AgencySubmission s : lapsed) {
            s.setStatus(SubmissionStatus.EXPIRED);
            submissions.save(s);
        }
        if (!lapsed.isEmpty()) log.info("Agency ownership sweep: {} submission(s) expired", lapsed.size());
        return lapsed.size();
    }

    @Scheduled(cron = "${hcm.recruitment.agency.expire-cron:0 45 2 * * *}")
    @Transactional
    public void dailyExpireSweep() {
        expireOverdue();
    }

    // ── Invoices ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listInvoices(InvoiceStatus status) {
        var rows = status == null ? invoices.findAllByOrderByCreatedAtDesc()
                : invoices.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(this::enrichInvoice).toList();
    }

    @Transactional
    public InvoiceResponse updateInvoice(UUID id, InvoiceUpdate req) {
        AgencyInvoice inv = invoices.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
        if (req.feeAmount() != null) {
            if (inv.getStatus() != InvoiceStatus.DRAFT) {
                throw new BadRequestException("Only a DRAFT invoice's amount can be changed");
            }
            if (req.feeAmount().signum() < 0) {
                throw new BadRequestException("Fee amount cannot be negative");
            }
            inv.setFeeAmount(req.feeAmount());
        }
        if (req.notes() != null) inv.setNotes(req.notes());
        if (req.status() != null && req.status() != inv.getStatus()) {
            transitionInvoice(inv, req.status());
        }
        inv.setUpdatedBy(currentRequest.username());
        AgencyInvoice saved = invoices.save(inv);
        return enrichInvoice(saved);
    }

    private void transitionInvoice(AgencyInvoice inv, InvoiceStatus target) {
        OffsetDateTime now = OffsetDateTime.now();
        switch (target) {
            case SENT -> {
                if (inv.getStatus() != InvoiceStatus.DRAFT) {
                    throw new BadRequestException("Only a DRAFT invoice can be sent");
                }
                inv.setSentAt(now);
            }
            case PAID -> {
                if (inv.getStatus() != InvoiceStatus.SENT) {
                    throw new BadRequestException("Only a SENT invoice can be marked paid");
                }
                inv.setPaidAt(now);
            }
            case CANCELLED -> {
                if (inv.getStatus() == InvoiceStatus.PAID) {
                    throw new BadRequestException("A paid invoice cannot be cancelled");
                }
            }
            case DRAFT -> throw new BadRequestException("Cannot move an invoice back to DRAFT");
        }
        InvoiceStatus from = inv.getStatus();
        inv.setStatus(target);
        audit.record(MODULE, "AgencyInvoice", inv.getId().toString(), "STATUS",
                Map.of("from", from.name()), Map.of("to", target.name()));
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private SubmissionResponse enrich(AgencySubmission s) {
        String agency = agencies.findById(s.getAgencyId()).map(Agency::getName).orElse("—");
        String cand = candidates.findById(s.getCandidateId()).map(this::candName).orElse("—");
        String vac = s.getVacancyId() == null ? null
                : vacancies.findById(s.getVacancyId()).map(Vacancy::getTitle).orElse(null);
        return SubmissionResponse.from(s, agency, cand, vac);
    }

    private InvoiceResponse enrichInvoice(AgencyInvoice i) {
        String agency = agencies.findById(i.getAgencyId()).map(Agency::getName).orElse("—");
        String cand = candidates.findById(i.getCandidateId()).map(this::candName).orElse("—");
        return InvoiceResponse.from(i, agency, cand);
    }

    private String candName(Candidate c) {
        return ((c.getFirstName() == null ? "" : c.getFirstName()) + " "
                + (c.getLastName() == null ? "" : c.getLastName())).trim();
    }
}
