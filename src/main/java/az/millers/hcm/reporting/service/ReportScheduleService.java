package az.millers.hcm.reporting.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.reporting.domain.ReportDefinition;
import az.millers.hcm.reporting.domain.ReportRun;
import az.millers.hcm.reporting.domain.ReportRunStatus;
import az.millers.hcm.reporting.domain.ReportSchedule;
import az.millers.hcm.reporting.domain.TriggerSource;
import az.millers.hcm.reporting.repo.ReportDefinitionRepository;
import az.millers.hcm.reporting.repo.ReportScheduleRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * CRUD on {@link ReportSchedule} plus a {@code @Scheduled} cron walker
 * that fires due schedules every minute.
 */
@Service
public class ReportScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduleService.class);
    private static final String MODULE = "REPORTING";
    private static final String ENTITY = "ReportSchedule";

    private final ReportScheduleRepository schedules;
    private final ReportDefinitionRepository definitions;
    private final ReportRunService runService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ReportScheduleService(ReportScheduleRepository schedules,
                                  ReportDefinitionRepository definitions,
                                  ReportRunService runService,
                                  AuditService audit,
                                  CurrentRequest currentRequest) {
        this.schedules = schedules;
        this.definitions = definitions;
        this.runService = runService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<ReportSchedule> list() {
        return schedules.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public ReportSchedule get(UUID id) {
        return schedules.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + id));
    }

    @Transactional
    public ReportSchedule create(String name, UUID definitionId, String cron,
                                   String recipients,
                                   az.millers.hcm.reporting.domain.WebhookType webhookType,
                                   String webhookUrl,
                                   Boolean active) {
        validateCron(cron);
        ReportDefinition def = definitions.findById(definitionId)
                .orElseThrow(() -> new BadRequestException("Definition not found: " + definitionId));

        ReportSchedule s = new ReportSchedule();
        s.setScheduleNo(String.format("RSC-%05d", schedules.nextNoSequence()));
        s.setName(name);
        s.setDefinitionId(def.getId());
        s.setCron(cron);
        s.setRecipients(recipients);
        s.setWebhookType(webhookType == null ? az.millers.hcm.reporting.domain.WebhookType.NONE : webhookType);
        s.setWebhookUrl(webhookUrl);
        s.setActive(active == null ? true : active);
        s.setNextRunAt(computeNext(cron, OffsetDateTime.now()));
        s.setCreatedBy(currentRequest.username());
        s.setUpdatedBy(currentRequest.username());
        ReportSchedule saved = schedules.save(s);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, Map.of(
                        "scheduleNo", saved.getScheduleNo(),
                        "cron", saved.getCron(),
                        "webhookType", String.valueOf(saved.getWebhookType()),
                        "nextRunAt", String.valueOf(saved.getNextRunAt())));
        return saved;
    }

    @Transactional
    public ReportSchedule update(UUID id, String name, String cron, String recipients,
                                   az.millers.hcm.reporting.domain.WebhookType webhookType,
                                   String webhookUrl,
                                   Boolean active) {
        ReportSchedule s = get(id);
        if (cron != null && !cron.isBlank() && !cron.equals(s.getCron())) {
            validateCron(cron);
            s.setCron(cron);
            s.setNextRunAt(computeNext(cron, OffsetDateTime.now()));
        }
        if (name != null && !name.isBlank()) s.setName(name);
        if (recipients != null) s.setRecipients(recipients);
        if (webhookType != null) s.setWebhookType(webhookType);
        if (webhookUrl != null) s.setWebhookUrl(webhookUrl);
        if (active != null) s.setActive(active);
        s.setUpdatedBy(currentRequest.username());
        return schedules.save(s);
    }

    /**
     * Cron walker, fires once a minute. Looks at active schedules with a
     * {@code nextRunAt} that has passed; for each, runs the associated
     * definition and advances {@code nextRunAt}.
     */
    @Scheduled(fixedDelayString = "${hcm.reporting.scheduler.delay-ms:60000}",
            initialDelayString = "${hcm.reporting.scheduler.initial-delay-ms:30000}")
    public void fireDue() {
        OffsetDateTime now = OffsetDateTime.now();
        List<ReportSchedule> due = schedules
                .findByActiveTrueAndNextRunAtBeforeOrderByNextRunAtAsc(now);
        for (ReportSchedule s : due) {
            try {
                runOne(s, now);
            } catch (Exception e) {
                log.error("Schedule {} ({}) failed: {}", s.getScheduleNo(), s.getName(), e.getMessage());
                s.setLastStatus("FAILED");
                s.setLastRunAt(OffsetDateTime.now());
                s.setNextRunAt(computeNext(s.getCron(), OffsetDateTime.now()));
                schedules.save(s);
            }
        }
    }

    @Transactional
    public ReportSchedule runOne(ReportSchedule s, OffsetDateTime now) {
        ReportDefinition def = definitions.findById(s.getDefinitionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Definition missing for schedule: " + s.getScheduleNo()));

        ReportRun run = runService.runAdhoc(
                def.getReportType(), def.getDefaultFormat(),
                def.getParameters(), def.getId(), s.getId(), TriggerSource.SCHEDULED);

        s.setLastRunAt(OffsetDateTime.now());
        s.setLastStatus(run.getStatus() == ReportRunStatus.SUCCESS ? "SUCCESS" : "FAILED");
        s.setNextRunAt(computeNext(s.getCron(), s.getLastRunAt()));
        ReportSchedule saved = schedules.save(s);
        log.info("Schedule {} fired → run {}", s.getScheduleNo(), run.getRunNo());
        return saved;
    }

    private static OffsetDateTime computeNext(String cron, OffsetDateTime after) {
        CronExpression expr = CronExpression.parse(cron);
        LocalDateTime baseline = after.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime next = expr.next(baseline);
        if (next == null) return null;
        return next.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static void validateCron(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new BadRequestException("cron is required (Spring 6-field format)");
        }
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid cron expression: " + e.getMessage());
        }
    }
}
