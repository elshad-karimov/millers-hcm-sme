package az.millers.hcm.lifecycle.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.CountByLabel;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.MonthlyCount;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.OffboardingReportResponse;
import az.millers.hcm.lifecycle.domain.OffboardingCase;
import az.millers.hcm.lifecycle.domain.OffboardingCaseStatus;
import az.millers.hcm.lifecycle.domain.ResignationRequest;
import az.millers.hcm.lifecycle.domain.SettlementStatus;
import az.millers.hcm.lifecycle.repo.OffboardingCaseRepository;
import az.millers.hcm.lifecycle.repo.OffboardingSettlementRepository;
import az.millers.hcm.lifecycle.repo.ResignationRequestRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OffboardingAnalyticsService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final OffboardingCaseRepository caseRepo;
    private final OffboardingSettlementRepository settlementRepo;
    private final ResignationRequestRepository resignationRepo;

    @Transactional(readOnly = true)
    public OffboardingReportResponse report(LocalDate from, LocalDate to) {
        // All cases created within window (using lastWorkingDate as the period anchor)
        List<OffboardingCase> all = caseRepo.findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable.unpaged()).getContent()
                .stream()
                .filter(c -> !c.getCreatedAt().toLocalDate().isBefore(from)
                        && !c.getCreatedAt().toLocalDate().isAfter(to))
                .toList();

        int total = all.size();
        int completed = (int) all.stream()
                .filter(c -> c.getCaseStatus() == OffboardingCaseStatus.COMPLETED).count();

        // Avg notice days from approved resignations in period
        OptionalDouble avgNotice = resignationRepo.findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .filter(r -> r.getNoticeDaysCalculated() != null
                        && r.getResignationDate() != null
                        && !r.getResignationDate().isBefore(from)
                        && !r.getResignationDate().isAfter(to))
                .mapToInt(ResignationRequest::getNoticeDaysCalculated)
                .average();

        // Rehire eligible %
        long rehireEligible = all.stream().filter(c -> Boolean.TRUE.equals(c.getRehireEligible())).count();
        double rehirePct = total == 0 ? 0.0 : (rehireEligible * 100.0 / total);

        // By source
        Map<String, Long> bySourceMap = new LinkedHashMap<>();
        all.forEach(c -> bySourceMap.merge(c.getSource().name(), 1L, Long::sum));
        List<CountByLabel> bySource = bySourceMap.entrySet().stream()
                .map(e -> new CountByLabel(e.getKey(), e.getValue())).toList();

        // By exit reason (top 5 non-null)
        Map<String, Long> byReasonMap = new LinkedHashMap<>();
        all.stream().filter(c -> c.getExitReason() != null && !c.getExitReason().isBlank())
                .forEach(c -> byReasonMap.merge(c.getExitReason(), 1L, Long::sum));
        List<CountByLabel> byExitReason = byReasonMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new CountByLabel(e.getKey(), e.getValue())).toList();

        // Monthly trend of case creation
        Map<String, Long> monthMap = new LinkedHashMap<>();
        all.forEach(c -> {
            String month = c.getCreatedAt().format(MONTH_FMT);
            monthMap.merge(month, 1L, Long::sum);
        });
        List<MonthlyCount> monthlyTrend = monthMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new MonthlyCount(e.getKey(), e.getValue()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // Settlement totals (PAID within period)
        BigDecimal totalGross = settlementRepo.sumGrossByStatusAndPaymentDateBetween(SettlementStatus.PAID, from, to);
        BigDecimal totalNet   = settlementRepo.sumNetByStatusAndPaymentDateBetween(SettlementStatus.PAID, from, to);

        return new OffboardingReportResponse(
                total, completed, avgNotice.orElse(0.0), rehirePct,
                totalGross, totalNet, bySource, byExitReason, monthlyTrend);
    }
}
