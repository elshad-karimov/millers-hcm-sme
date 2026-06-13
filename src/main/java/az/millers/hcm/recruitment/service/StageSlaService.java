package az.millers.hcm.recruitment.service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.StageSlaDtos.SlaBreachReport;
import az.millers.hcm.recruitment.api.dto.StageSlaDtos.SlaBreachRow;
import az.millers.hcm.recruitment.api.dto.StageSlaDtos.SlaConfig;
import az.millers.hcm.recruitment.api.dto.StageSlaDtos.SlaConfigUpdate;
import az.millers.hcm.recruitment.domain.Application;
import az.millers.hcm.recruitment.domain.ApplicationEvent;
import az.millers.hcm.recruitment.domain.ApplicationStatus;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.EventType;
import az.millers.hcm.recruitment.domain.StageSla;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.repo.ApplicationEventRepository;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;
import az.millers.hcm.recruitment.repo.StageSlaRepository;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M288 — Recruitment PRD §14/§43: pipeline stage SLA + overdue
 * tracking. Config CRUD over the per-stage SLA, a breach report that
 * computes time-in-stage from the M45 ApplicationEvent timeline, and
 * a daily sweep that audits the overdue count.
 */
@Service
public class StageSlaService {

    private static final Logger log = LoggerFactory.getLogger(StageSlaService.class);
    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "StageSla";

    private final StageSlaRepository slas;
    private final ApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final CandidateRepository candidates;
    private final VacancyRepository vacancies;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public StageSlaService(StageSlaRepository slas,
                            ApplicationRepository applications,
                            ApplicationEventRepository events,
                            CandidateRepository candidates,
                            VacancyRepository vacancies,
                            AuditService audit,
                            CurrentRequest currentRequest) {
        this.slas = slas;
        this.applications = applications;
        this.events = events;
        this.candidates = candidates;
        this.vacancies = vacancies;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<SlaConfig> listConfig() {
        return slas.findAllByOrderByStageAsc().stream().map(SlaConfig::from).toList();
    }

    @Transactional
    public SlaConfig updateConfig(UUID id, SlaConfigUpdate req) {
        StageSla s = slas.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SLA config not found: " + id));
        s.setSlaDays(req.slaDays());
        s.setOwnerRole(req.ownerRole());
        if (req.active() != null) s.setActive(req.active());
        s.setUpdatedBy(currentRequest.username());
        StageSla saved = slas.save(s);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", null,
                Map.of("stage", saved.getStage().name(),
                        "slaDays", saved.getSlaDays(),
                        "ownerRole", saved.getOwnerRole() == null ? "" : saved.getOwnerRole()));
        return SlaConfig.from(saved);
    }

    /**
     * The breach report (PRD §43). For every in-flight application,
     * compute days-in-current-stage and compare to that stage's SLA.
     * DUE_SOON = within 1 day of breaching; OVERDUE = past it.
     */
    @Transactional(readOnly = true)
    public SlaBreachReport breaches() {
        Map<az.millers.hcm.recruitment.domain.ApplicationStage, StageSla> byStage = new java.util.HashMap<>();
        for (StageSla s : slas.findAllByOrderByStageAsc()) {
            if (s.isActive()) byStage.put(s.getStage(), s);
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<SlaBreachRow> rows = new ArrayList<>();
        int overdue = 0, dueSoon = 0;

        for (Application a : applications.findByStatus(ApplicationStatus.IN_PROGRESS)) {
            StageSla sla = byStage.get(a.getCurrentStage());
            if (sla == null) continue; // no SLA configured for this stage

            OffsetDateTime enteredAt = stageEnteredAt(a);
            long daysInStage = ChronoUnit.DAYS.between(enteredAt, now);
            long daysOver = daysInStage - sla.getSlaDays();
            String severity;
            if (daysOver > 0) { severity = "OVERDUE"; overdue++; }
            else if (daysOver == 0) { severity = "DUE_SOON"; dueSoon++; }
            else continue; // comfortably within SLA

            Candidate c = candidates.findById(a.getCandidateId()).orElse(null);
            Vacancy v = vacancies.findById(a.getVacancyId()).orElse(null);
            rows.add(new SlaBreachRow(
                    a.getId(), a.getApplicationNo(), a.getCandidateId(),
                    c == null ? "—" : (nz(c.getFirstName()) + " " + nz(c.getLastName())).trim(),
                    v == null ? "—" : v.getTitle(),
                    a.getCurrentStage(), sla.getOwnerRole(),
                    daysInStage, sla.getSlaDays(), Math.max(0, daysOver),
                    severity));
        }
        rows.sort(Comparator.comparingLong(SlaBreachRow::daysOver).reversed());
        return new SlaBreachReport(overdue, dueSoon, rows);
    }

    /**
     * When the application entered its current stage: the timestamp of
     * the most recent STAGE_CHANGE event landing on the current stage,
     * falling back to the application's creation time (CV_SCREENING is
     * the implicit first stage with no event).
     */
    private OffsetDateTime stageEnteredAt(Application a) {
        OffsetDateTime entered = a.getCreatedAt();
        for (ApplicationEvent e : events.findByApplicationIdOrderByCreatedAtAsc(a.getId())) {
            if (e.getEventType() == EventType.STAGE_CHANGE
                    && e.getToStage() == a.getCurrentStage()
                    && e.getCreatedAt() != null) {
                entered = e.getCreatedAt(); // keep the latest matching entry
            }
        }
        return entered;
    }

    /**
     * M288 — daily 01:00 sweep: audit the current overdue count so the
     * breach trend is captured even when no one opens the report.
     * Mirrors the M89 stale-pool scheduler's lightweight approach.
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional(readOnly = true)
    public void dailyOverdueSweep() {
        SlaBreachReport report = breaches();
        if (report.overdueCount() > 0) {
            audit.record(MODULE, "SlaSweep", "daily", "OVERDUE_SWEEP", null,
                    Map.of("overdue", report.overdueCount(),
                            "dueSoon", report.dueSoonCount()));
            log.info("StageSlaService: {} application(s) overdue, {} due soon",
                    report.overdueCount(), report.dueSoonCount());
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
