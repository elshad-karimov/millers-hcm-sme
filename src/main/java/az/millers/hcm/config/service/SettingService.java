package az.millers.hcm.config.service;
import az.millers.hcm.common.tenant.TenantContext;

import az.millers.hcm.config.domain.TenantSetting;
import az.millers.hcm.config.repo.TenantSettingRepository;
import az.millers.hcm.security.CurrentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

/**
 * M433 — Tenant setting service. manager_can_view_salary default false.
 */
@Service
public class SettingService {
    public static final String MANAGER_CAN_VIEW_SALARY = "manager_can_view_salary";

    private final TenantSettingRepository repo;
    private final CurrentRequest currentRequest;

    public SettingService(TenantSettingRepository repo, CurrentRequest currentRequest) {
        this.repo = repo;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        return repo.findByTenantIdAndKey(TenantContext.current(), key)
                .map(TenantSetting::getValue)
                .orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        TenantSetting s = repo.findByTenantIdAndKey(TenantContext.current(), key).orElseGet(() -> {
            TenantSetting ns = new TenantSetting();
            ns.setTenantId(TenantContext.current());
            ns.setKey(key);
            ns.setCreatedBy(currentRequest.username());
            return ns;
        });
        s.setValue(value);
        s.setUpdatedBy(currentRequest.username());
        s.setUpdatedAt(OffsetDateTime.now());
        repo.save(s);
    }

    public boolean managerCanViewSalary() {
        return "true".equalsIgnoreCase(get(MANAGER_CAN_VIEW_SALARY, "false"));
    }
}
