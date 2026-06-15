package az.millers.hcm.recruitment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.api.dto.AddBonusRequest;
import az.millers.hcm.payroll.domain.BonusType;
import az.millers.hcm.payroll.domain.PayrollBonus;
import az.millers.hcm.payroll.service.PayrollRunService;
import az.millers.hcm.recruitment.api.dto.ReferralDtos.ReferralRequest;
import az.millers.hcm.recruitment.api.dto.ReferralDtos.ReferralResponse;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.Referral;
import az.millers.hcm.recruitment.domain.ReferralStatus;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.repo.CandidateRepository;
import az.millers.hcm.recruitment.repo.ReferralRepository;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M295 — Recruitment PRD Phase F: employee referral program.
 *
 * <p>An employee refers a candidate (SUBMITTED). When the candidate is
 * hired the referral flips to HIRED and a qualifying clock starts; once
 * elapsed a daily sweep promotes it to QUALIFIED. HR then pays the bonus
 * into a payroll run — reusing {@link PayrollRunService#addBonus} — which
 * marks it PAID with a backref to the created payroll bonus.
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);
    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Referral";

    private final ReferralRepository referrals;
    private final CandidateRepository candidates;
    private final VacancyRepository vacancies;
    private final EmployeeRepository employees;
    private final PayrollRunService payrollRunService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final BigDecimal defaultBonus;
    private final int defaultQualifyingDays;
    private final String defaultCurrency;

    public ReferralService(ReferralRepository referrals,
                            CandidateRepository candidates,
                            VacancyRepository vacancies,
                            EmployeeRepository employees,
                            PayrollRunService payrollRunService,
                            AuditService audit,
                            CurrentRequest currentRequest,
                            @Value("${hcm.recruitment.referral.default-bonus:500}") BigDecimal defaultBonus,
                            @Value("${hcm.recruitment.referral.qualifying-days:90}") int defaultQualifyingDays,
                            @Value("${hcm.recruitment.referral.currency:AZN}") String defaultCurrency) {
        this.referrals = referrals;
        this.candidates = candidates;
        this.vacancies = vacancies;
        this.employees = employees;
        this.payrollRunService = payrollRunService;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.defaultBonus = defaultBonus;
        this.defaultQualifyingDays = defaultQualifyingDays;
        this.defaultCurrency = defaultCurrency;
    }

    // ── Queries ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReferralResponse> list(ReferralStatus status) {
        List<Referral> rows = status == null
                ? referrals.findAllByOrderByCreatedAtDesc()
                : referrals.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(this::enrich).toList();
    }

    @Transactional(readOnly = true)
    public ReferralResponse get(UUID id) {
        return enrich(load(id));
    }

    private Referral load(UUID id) {
        return referrals.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Referral not found: " + id));
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Transactional
    public ReferralResponse create(ReferralRequest req) {
        if (!employees.existsById(req.referrerEmployeeId())) {
            throw new BadRequestException("Referrer employee not found: " + req.referrerEmployeeId());
        }
        Candidate cand = candidates.findById(req.candidateId())
                .orElseThrow(() -> new BadRequestException("Candidate not found: " + req.candidateId()));
        if (referrals.existsByCandidateId(cand.getId())) {
            throw new BadRequestException("This candidate already has a referral on file");
        }
        if (req.vacancyId() != null && !vacancies.existsById(req.vacancyId())) {
            throw new BadRequestException("Vacancy not found: " + req.vacancyId());
        }
        Referral r = new Referral();
        r.setReferralNo(String.format("REF-%05d", referrals.nextNoSequence()));
        r.setReferrerEmployeeId(req.referrerEmployeeId());
        r.setCandidateId(cand.getId());
        r.setVacancyId(req.vacancyId());
        r.setStatus(ReferralStatus.SUBMITTED);
        r.setBonusAmount(req.bonusAmount() != null ? req.bonusAmount() : defaultBonus);
        r.setCurrency(req.currency() != null ? req.currency() : defaultCurrency);
        r.setQualifyingDays(req.qualifyingDays() != null ? req.qualifyingDays() : defaultQualifyingDays);
        r.setNotes(req.notes());
        r.setCreatedBy(currentRequest.username());
        r.setUpdatedBy(currentRequest.username());
        Referral saved = referrals.save(r);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null,
                Map.of("referralNo", saved.getReferralNo(),
                        "candidateId", cand.getId().toString(),
                        "referrer", req.referrerEmployeeId().toString()));
        return enrich(saved);
    }

    @Transactional
    public ReferralResponse reject(UUID id, String reason) {
        Referral r = load(id);
        if (r.getStatus() == ReferralStatus.PAID) {
            throw new BadRequestException("A paid referral cannot be rejected");
        }
        r.setStatus(ReferralStatus.REJECTED);
        if (reason != null && !reason.isBlank()) r.setNotes(reason);
        r.setUpdatedBy(currentRequest.username());
        Referral saved = referrals.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "REJECT", null,
                Map.of("reason", reason == null ? "" : reason));
        return enrich(saved);
    }

    /**
     * Hook from {@link ApplicationService#hire}: when a referred candidate
     * is hired, start the qualifying clock. Best-effort — never blocks the
     * hire. No-op if there's no referral or it isn't SUBMITTED.
     */
    @Transactional
    public void markHiredForCandidate(UUID candidateId, LocalDate hireDate) {
        Referral r = referrals.findByCandidateId(candidateId).orElse(null);
        if (r == null || r.getStatus() != ReferralStatus.SUBMITTED) return;
        OffsetDateTime hired = hireDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        r.setStatus(ReferralStatus.HIRED);
        r.setHiredAt(hired);
        r.setQualifiedAt(hired.plusDays(r.getQualifyingDays()));
        referrals.save(r);
        audit.record(MODULE, ENTITY, r.getId().toString(), "HIRED", null,
                Map.of("hireDate", hireDate.toString(),
                        "qualifiesOn", r.getQualifiedAt().toLocalDate().toString()));
        log.info("Referral {} → HIRED (qualifies {})", r.getReferralNo(),
                r.getQualifiedAt().toLocalDate());
    }

    /** Promote HIRED referrals past their qualifying clock to QUALIFIED. */
    @Transactional
    public int promoteQualified() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Referral> due = referrals.findByStatusAndQualifiedAtLessThanEqual(
                ReferralStatus.HIRED, now);
        for (Referral r : due) {
            r.setStatus(ReferralStatus.QUALIFIED);
            referrals.save(r);
            audit.record(MODULE, ENTITY, r.getId().toString(), "QUALIFIED", null, null);
        }
        if (!due.isEmpty()) log.info("Referral sweep: {} referral(s) → QUALIFIED", due.size());
        return due.size();
    }

    @Scheduled(cron = "${hcm.recruitment.referral.qualify-cron:0 15 2 * * *}")
    @Transactional
    public void dailyQualifySweep() {
        promoteQualified();
    }

    /**
     * Pay a QUALIFIED referral into a payroll run: inserts a ONE_TIME bonus
     * for the referrer (M15) and marks the referral PAID with a backref.
     */
    @Transactional
    public ReferralResponse pay(UUID id, UUID payrollRunId) {
        Referral r = load(id);
        if (r.getStatus() != ReferralStatus.QUALIFIED) {
            throw new BadRequestException(
                    "Only a QUALIFIED referral can be paid (current: " + r.getStatus() + ")");
        }
        Candidate cand = candidates.findById(r.getCandidateId()).orElse(null);
        String note = "Referral " + r.getReferralNo() + " — candidate "
                + (cand == null ? r.getCandidateId().toString() : fullName(cand));
        PayrollBonus bonus = payrollRunService.addBonus(payrollRunId,
                new AddBonusRequest(r.getReferrerEmployeeId(), BonusType.ONE_TIME,
                        r.getBonusAmount(), note));

        r.setStatus(ReferralStatus.PAID);
        r.setPaidAt(OffsetDateTime.now());
        r.setPayrollRunId(payrollRunId);
        r.setPayrollBonusId(bonus.getId());
        r.setUpdatedBy(currentRequest.username());
        Referral saved = referrals.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "PAY", null,
                Map.of("payrollRunId", payrollRunId.toString(),
                        "payrollBonusId", bonus.getId().toString(),
                        "amount", r.getBonusAmount().toPlainString()));
        log.info("Referral {} paid into payroll run {} (bonus {})",
                r.getReferralNo(), payrollRunId, bonus.getId());
        return enrich(saved);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private ReferralResponse enrich(Referral r) {
        String referrer = employees.findById(r.getReferrerEmployeeId())
                .map(e -> fullName(e.getFirstName(), e.getLastName())).orElse("—");
        String candidate = candidates.findById(r.getCandidateId())
                .map(this::fullName).orElse("—");
        String vacancy = r.getVacancyId() == null ? null
                : vacancies.findById(r.getVacancyId()).map(Vacancy::getTitle).orElse(null);
        return ReferralResponse.from(r, referrer, candidate, vacancy);
    }

    private String fullName(Candidate c) {
        return fullName(c.getFirstName(), c.getLastName());
    }

    private String fullName(String first, String last) {
        String n = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return n.isEmpty() ? "—" : n;
    }

    private static String fullName(Employee e) {
        return e.getFirstName() + " " + e.getLastName();
    }
}
