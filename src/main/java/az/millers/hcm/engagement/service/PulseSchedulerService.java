package az.millers.hcm.engagement.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.engagement.domain.CampaignStatus;
import az.millers.hcm.engagement.domain.PulseFrequency;
import az.millers.hcm.engagement.domain.PulseSchedule;
import az.millers.hcm.engagement.domain.SurveyCampaign;
import az.millers.hcm.engagement.domain.SurveyTemplate;
import az.millers.hcm.engagement.repo.PulseScheduleRepository;
import az.millers.hcm.engagement.repo.SurveyCampaignRepository;
import az.millers.hcm.engagement.repo.SurveyTemplateRepository;

/**
 * M477 — Pulse survey scheduler.
 * Daily sweep at 05:15 creates SurveyCampaign per active pulse schedule.
 */
@Service
public class PulseSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(PulseSchedulerService.class);

    private final PulseScheduleRepository pulseRepo;
    private final SurveyTemplateRepository templateRepo;
    private final SurveyCampaignRepository campaignRepo;

    public PulseSchedulerService(PulseScheduleRepository pulseRepo,
                                 SurveyTemplateRepository templateRepo,
                                 SurveyCampaignRepository campaignRepo) {
        this.pulseRepo = pulseRepo;
        this.templateRepo = templateRepo;
        this.campaignRepo = campaignRepo;
    }

    /**
     * Daily sweep at 05:15 to create campaigns from active pulse schedules.
     */
    @Scheduled(cron = "0 15 5 * * ?")
    @Transactional
    public void dailySweep() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        log.debug("Pulse scheduler sweep for {}", today);

        List<PulseSchedule> activeSchedules = pulseRepo.findByTenantIdAndActiveOrderByCreatedAtDesc(TenantContext.current(), true);

        for (PulseSchedule schedule : activeSchedules) {
            if (isDue(schedule, today)) {
                createCampaign(schedule, today);
                schedule.setLastRunAt(OffsetDateTime.now());
                pulseRepo.save(schedule);
            }
        }
    }

    private boolean isDue(PulseSchedule schedule, LocalDate today) {
        // Check if already ran today (idempotency)
        if (schedule.getLastRunAt() != null) {
            LocalDate lastRun = schedule.getLastRunAt().toLocalDate();
            if (!lastRun.isBefore(today)) {
                return false; // Already ran today or in the future
            }
        }

        return switch (schedule.getFrequency()) {
            case WEEKLY -> {
                if (schedule.getDayOfWeek() == null) yield false;
                yield today.getDayOfWeek().getValue() == schedule.getDayOfWeek();
            }
            case BIWEEKLY -> {
                if (schedule.getDayOfWeek() == null) yield false;
                if (today.getDayOfWeek().getValue() != schedule.getDayOfWeek()) yield false;
                // Check if it's been ~14 days since last run
                if (schedule.getLastRunAt() == null) yield true;
                long daysSinceLastRun = java.time.temporal.ChronoUnit.DAYS.between(
                        schedule.getLastRunAt().toLocalDate(), today);
                yield daysSinceLastRun >= 14;
            }
            case MONTHLY -> {
                if (schedule.getDayOfMonth() == null) yield false;
                yield today.getDayOfMonth() == schedule.getDayOfMonth();
            }
        };
    }

    private void createCampaign(PulseSchedule schedule, LocalDate today) {
        SurveyTemplate template = templateRepo.findById(schedule.getSurveyTemplateId())
                .orElse(null);

        if (template == null || !template.isActive()) {
            log.warn("Pulse schedule {} references inactive or missing template {}",
                    schedule.getId(), schedule.getSurveyTemplateId());
            return;
        }

        // Generate campaign name with date
        String campaignName = template.getName() + " - " + today.toString();

        // Check if campaign already exists for this period (simple duplicate check)
        List<SurveyCampaign> existing = campaignRepo.findByTemplateIdOrderByCreatedAtDesc(template.getId());
        boolean alreadyCreatedToday = existing.stream()
                .anyMatch(c -> c.getName().equals(campaignName));
        if (alreadyCreatedToday) {
            log.debug("Campaign already exists: {}", campaignName);
            return;
        }

        // Create campaign: opens today, closes in 7 days
        SurveyCampaign campaign = new SurveyCampaign();
        campaign.setTemplateId(template.getId());
        campaign.setName(campaignName);
        campaign.setDescription("Auto-generated pulse survey");
        campaign.setOpensOn(today);
        campaign.setClosesOn(today.plusDays(7));
        campaign.setStatus(CampaignStatus.DRAFT); // Existing scheduler will activate it
        campaign.setTargetAll(true);
        campaign.setCreatedBy("system:pulse-scheduler");

        campaignRepo.save(campaign);
        log.info("Created pulse campaign: {} ({})", campaignName, campaign.getId());
    }
}
