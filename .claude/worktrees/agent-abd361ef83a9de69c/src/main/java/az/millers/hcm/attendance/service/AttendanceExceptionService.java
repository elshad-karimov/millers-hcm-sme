package az.millers.hcm.attendance.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import az.millers.hcm.attendance.api.dto.ExceptionDtos.AttendanceExceptionResponse;
import az.millers.hcm.attendance.api.dto.ExceptionDtos.ExceptionConfigRequest;
import az.millers.hcm.attendance.api.dto.ExceptionDtos.ExceptionConfigResponse;
import az.millers.hcm.attendance.domain.AttendanceException;
import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.ExceptionConfig;
import az.millers.hcm.attendance.events.ExceptionGeneratedEvent;
import az.millers.hcm.attendance.repo.AttendanceExceptionRepository;
import az.millers.hcm.attendance.repo.ExceptionConfigRepository;
import az.millers.hcm.attendance.repo.OvertimeRequestRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.organization.repo.LegalEntityRepository;

/**
 * M332: Attendance exception service.
 *
 * <p>Generates exceptions from daily summaries based on configured rules,
 * manages acknowledgment and resolution.
 */
@Service
@Transactional
public class AttendanceExceptionService {

    private static final String MODULE = "attendance";
    private static final String ENTITY_TYPE = "attendance_exception";

    private final AttendanceExceptionRepository exceptionRepository;
    private final ExceptionConfigRepository configRepository;
    private final OvertimeRequestRepository overtimeRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final LegalEntityRepository legalEntities;

    public AttendanceExceptionService(AttendanceExceptionRepository exceptionRepository,
                                       ExceptionConfigRepository configRepository,
                                       OvertimeRequestRepository overtimeRepository,
                                       AuditService auditService,
                                       ApplicationEventPublisher eventPublisher,
                                       LegalEntityRepository legalEntities) {
        this.exceptionRepository = exceptionRepository;
        this.configRepository = configRepository;
        this.overtimeRepository = overtimeRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.legalEntities = legalEntities;
    }

    public void generateForSummary(DailySummary summary, UUID tenantId) {
        List<ExceptionConfig> configs = configRepository.findByTenantIdAndEnabledTrue(tenantId);

        for (ExceptionConfig config : configs) {
            boolean shouldGenerate = false;
            int actualMinutes = 0;

            switch (config.getExceptionType()) {
                case "LATE_ARRIVAL":
                    if (summary.getLateMinutes() > config.getThresholdMinutes()) {
                        shouldGenerate = true;
                        actualMinutes = summary.getLateMinutes();
                    }
                    break;

                case "EARLY_DEPARTURE":
                    if (summary.getEarlyMinutes() > config.getThresholdMinutes()) {
                        shouldGenerate = true;
                        actualMinutes = summary.getEarlyMinutes();
                    }
                    break;

                case "ABSENT":
                    if ("ABSENT".equals(summary.getStatus().name())) {
                        shouldGenerate = true;
                    }
                    break;

                case "MISSING_CLOCK_OUT":
                    if (summary.getExitTime() == null && !"ABSENT".equals(summary.getStatus().name())) {
                        shouldGenerate = true;
                    }
                    break;

                case "EXCESS_OT":
                    if (summary.getOvertimeMinutes() > config.getThresholdMinutes()) {
                        shouldGenerate = true;
                        actualMinutes = summary.getOvertimeMinutes();
                    }
                    break;

                case "UNAUTHORIZED_OT":
                    if (summary.getOvertimeMinutes() > config.getThresholdMinutes()) {
                        List<?> approvedRequests = overtimeRepository.findByTenantIdAndEmployeeIdAndWorkDateAndDecision(
                                tenantId, summary.getEmployeeId(), summary.getWorkDate(), "APPROVED");
                        if (approvedRequests.isEmpty()) {
                            shouldGenerate = true;
                            actualMinutes = summary.getOvertimeMinutes();
                        }
                    }
                    break;

                default:
                    break;
            }

            if (shouldGenerate) {
                boolean alreadyExists = exceptionRepository.findByTenantIdAndEmployeeIdAndWorkDateAndExceptionType(
                        tenantId, summary.getEmployeeId(), summary.getWorkDate(), config.getExceptionType())
                        .isPresent();

                if (!alreadyExists) {
                    AttendanceException exception = new AttendanceException();
                    exception.setTenantId(tenantId);
                    exception.setEmployeeId(summary.getEmployeeId());
                    exception.setWorkDate(summary.getWorkDate());
                    exception.setSummaryId(summary.getId());
                    exception.setExceptionType(config.getExceptionType());
                    exception.setSeverity(config.getSeverity());
                    exception.setThresholdMinutes(config.getThresholdMinutes());
                    exception.setActualMinutes(actualMinutes);
                    exception.setStatus("OPEN");

                    exception = exceptionRepository.save(exception);

                    auditService.record(MODULE, ENTITY_TYPE, exception.getId().toString(),
                            "EXCEPTION_GENERATED", null,
                            Map.of("type", config.getExceptionType(), "severity", config.getSeverity()));

                    eventPublisher.publishEvent(new ExceptionGeneratedEvent(exception, tenantId));
                }
            }
        }
    }

    public AttendanceExceptionResponse acknowledge(UUID id, String by) {
        UUID tenantId = defaultTenantId();
        AttendanceException exception = exceptionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Exception not found: " + id));

        exception.setStatus("ACKNOWLEDGED");
        exception.setAcknowledgedBy(by);
        exception.setAcknowledgedAt(OffsetDateTime.now());

        exception = exceptionRepository.save(exception);

        auditService.record(MODULE, ENTITY_TYPE, id.toString(),
                "EXCEPTION_ACKNOWLEDGED", null, Map.of());

        return AttendanceExceptionResponse.from(exception);
    }

    public AttendanceExceptionResponse resolve(UUID id, String by, String notes) {
        UUID tenantId = defaultTenantId();
        AttendanceException exception = exceptionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Exception not found: " + id));

        exception.setStatus("RESOLVED");
        exception.setResolvedBy(by);
        exception.setResolvedAt(OffsetDateTime.now());
        exception.setNotes(notes);

        exception = exceptionRepository.save(exception);

        auditService.record(MODULE, ENTITY_TYPE, id.toString(),
                "EXCEPTION_RESOLVED", null, Map.of("notes", notes != null ? notes : ""));

        return AttendanceExceptionResponse.from(exception);
    }

    @Transactional(readOnly = true)
    public List<AttendanceExceptionResponse> list(String status) {
        UUID tenantId = defaultTenantId();
        List<AttendanceException> exceptions = status != null
                ? exceptionRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status)
                : exceptionRepository.findByTenantIdOrderByWorkDateDescCreatedAtDesc(tenantId);

        return exceptions.stream()
                .map(AttendanceExceptionResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceExceptionResponse> listByEmployee(UUID employeeId) {
        UUID tenantId = defaultTenantId();
        return exceptionRepository.findByTenantIdAndEmployeeIdOrderByWorkDateDesc(tenantId, employeeId).stream()
                .map(AttendanceExceptionResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ExceptionConfigResponse> getConfigs() {
        UUID tenantId = defaultTenantId();
        return configRepository.findByTenantIdOrderByExceptionTypeAsc(tenantId).stream()
                .map(ExceptionConfigResponse::from)
                .collect(Collectors.toList());
    }

    public ExceptionConfigResponse updateConfig(UUID id, ExceptionConfigRequest req) {
        UUID tenantId = defaultTenantId();
        ExceptionConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Exception config not found: " + id));

        if (!config.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Config not found for tenant");
        }

        config.setThresholdMinutes(req.thresholdMinutes());
        config.setSeverity(req.severity());
        config.setEnabled(req.enabled());
        config.setAutoNotify(req.autoNotify());

        config = configRepository.save(config);

        auditService.record(MODULE, "exception_config", id.toString(),
                "CONFIG_UPDATED", null,
                Map.of("type", config.getExceptionType(), "enabled", req.enabled()));

        return ExceptionConfigResponse.from(config);
    }

    private UUID defaultTenantId() {
        return legalEntities.findAllByOrderByCodeAsc().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No legal entity found")).getId();
    }
}
