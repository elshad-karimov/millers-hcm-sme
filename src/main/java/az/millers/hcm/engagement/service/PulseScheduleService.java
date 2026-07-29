package az.millers.hcm.engagement.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.engagement.domain.PulseSchedule;
import az.millers.hcm.engagement.repo.PulseScheduleRepository;

/**
 * M477 — Pulse schedule CRUD service.
 */
@Service
public class PulseScheduleService {


    private final PulseScheduleRepository repository;

    public PulseScheduleService(PulseScheduleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PulseSchedule> listActive() {
        return repository.findByTenantIdAndActiveOrderByCreatedAtDesc(TenantContext.current(), true);
    }

    @Transactional(readOnly = true)
    public List<PulseSchedule> listAll() {
        return repository.findByTenantIdAndActiveOrderByCreatedAtDesc(TenantContext.current(), null);
    }

    @Transactional(readOnly = true)
    public PulseSchedule get(UUID id) {
        return repository.findById(id)
                .filter(s -> TenantContext.current().equals(s.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Pulse schedule not found"));
    }

    @Transactional
    public PulseSchedule create(PulseSchedule schedule) {
        schedule.setTenantId(TenantContext.current());
        return repository.save(schedule);
    }

    @Transactional
    public PulseSchedule update(UUID id, PulseSchedule updates) {
        PulseSchedule existing = get(id);
        existing.setSurveyTemplateId(updates.getSurveyTemplateId());
        existing.setFrequency(updates.getFrequency());
        existing.setDayOfWeek(updates.getDayOfWeek());
        existing.setDayOfMonth(updates.getDayOfMonth());
        existing.setActive(updates.getActive());
        return repository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        PulseSchedule schedule = get(id);
        repository.delete(schedule);
    }
}
