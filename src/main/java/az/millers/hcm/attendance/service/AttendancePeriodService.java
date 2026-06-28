package az.millers.hcm.attendance.service;

import java.time.LocalDate;
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

import az.millers.hcm.attendance.api.dto.PeriodDtos.PeriodResponse;
import az.millers.hcm.attendance.domain.AttendancePeriod;
import az.millers.hcm.attendance.events.PeriodLockedEvent;
import az.millers.hcm.attendance.repo.AttendancePeriodRepository;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.organization.repo.LegalEntityRepository;

/**
 * M330: Attendance period lock service.
 *
 * <p>Manages period status (OPEN | LOCKED | CLOSED). Once locked, corrections
 * require special permission or unlock.
 */
@Service
@Transactional
public class AttendancePeriodService {

    private static final String MODULE = "attendance";
    private static final String ENTITY_TYPE = "attendance_period";

    private final AttendancePeriodRepository repository;
    private final DailySummaryRepository summaryRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final LegalEntityRepository legalEntities;

    public AttendancePeriodService(AttendancePeriodRepository repository,
                                    DailySummaryRepository summaryRepository,
                                    AuditService auditService,
                                    ApplicationEventPublisher eventPublisher,
                                    LegalEntityRepository legalEntities) {
        this.repository = repository;
        this.summaryRepository = summaryRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.legalEntities = legalEntities;
    }

    public PeriodResponse getOrCreate(int year, int month) {
        UUID tenantId = defaultTenantId();
        AttendancePeriod period = repository.findByTenantIdAndYearAndMonth(tenantId, year, month)
                .orElseGet(() -> {
                    AttendancePeriod newPeriod = new AttendancePeriod();
                    newPeriod.setTenantId(tenantId);
                    newPeriod.setYear(year);
                    newPeriod.setMonth(month);
                    newPeriod.setStatus("OPEN");
                    return repository.save(newPeriod);
                });
        return PeriodResponse.from(period);
    }

    public PeriodResponse lock(int year, int month, String notes, String lockedBy) {
        UUID tenantId = defaultTenantId();
        AttendancePeriod period = repository.findByTenantIdAndYearAndMonth(tenantId, year, month)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Period not found: " + year + "-" + month));

        if (!"OPEN".equals(period.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Period is not OPEN, current status: " + period.getStatus());
        }

        LocalDate firstDay = LocalDate.of(year, month, 1);
        long employeeCount = summaryRepository.countDistinctEmployeesByWorkDate(firstDay);

        period.setStatus("LOCKED");
        period.setLockedAt(OffsetDateTime.now());
        period.setLockedBy(lockedBy);
        period.setEmployeeCountAtLock((int) employeeCount);
        period.setNotes(notes);

        period = repository.save(period);

        auditService.record(MODULE, ENTITY_TYPE, period.getId().toString(),
                "PERIOD_LOCKED", null,
                Map.of("year", year, "month", month, "employeeCount", employeeCount));

        eventPublisher.publishEvent(new PeriodLockedEvent(tenantId, year, month, lockedBy));

        return PeriodResponse.from(period);
    }

    public PeriodResponse unlock(int year, int month, String unlockedBy) {
        UUID tenantId = defaultTenantId();
        AttendancePeriod period = repository.findByTenantIdAndYearAndMonth(tenantId, year, month)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Period not found: " + year + "-" + month));

        if (!"LOCKED".equals(period.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Period is not LOCKED, current status: " + period.getStatus());
        }

        period.setStatus("OPEN");
        period.setUnlockedAt(OffsetDateTime.now());
        period.setUnlockedBy(unlockedBy);

        period = repository.save(period);

        auditService.record(MODULE, ENTITY_TYPE, period.getId().toString(),
                "PERIOD_UNLOCKED", null,
                Map.of("year", year, "month", month));

        return PeriodResponse.from(period);
    }

    @Transactional(readOnly = true)
    public List<PeriodResponse> list() {
        UUID tenantId = defaultTenantId();
        return repository.findByTenantIdOrderByYearDescMonthDesc(tenantId).stream()
                .map(PeriodResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean isLocked(int year, int month) {
        UUID tenantId = defaultTenantId();
        return repository.findByTenantIdAndYearAndMonth(tenantId, year, month)
                .map(p -> "LOCKED".equals(p.getStatus()))
                .orElse(false);
    }

    private UUID defaultTenantId() {
        return legalEntities.findAllByOrderByCodeAsc().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No legal entity found")).getId();
    }
}
