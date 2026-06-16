package az.millers.hcm.lifecycle.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.domain.ChecklistTemplate;
import az.millers.hcm.lifecycle.domain.ChecklistTemplateRule;
import az.millers.hcm.lifecycle.repo.ChecklistTemplateRepository;
import az.millers.hcm.lifecycle.repo.ChecklistTemplateRuleRepository;

/**
 * Selects the checklist template that best fits a given employee for a flow
 * (M298 — Onboarding Phase A.1).
 *
 * <p>Single responsibility: given an {@link Employee} and a
 * {@link ChecklistFlowType}, rank the active templates by how specifically
 * their rules match the employee. The winner is the template with the highest
 * matching-rule specificity (most non-null filter columns), tie-broken by
 * template {@code priority} (desc) then {@code code} (asc).
 *
 * <p>A template with no rules is a catch-all default with specificity 0, so it
 * applies only when no rule-bearing template matches — this preserves the
 * pre-M298 "every hire gets the default onboarding checklist" behaviour while
 * letting HR layer entity/department/position-specific templates on top.
 *
 * <p>Reused by both ONBOARDING (auto-select on hire) and OFFBOARDING flows, and
 * by the match-preview endpoint that shows HR which template a hire would get.
 */
@Component
public class ChecklistTemplateMatcher {

    private final ChecklistTemplateRepository templates;
    private final ChecklistTemplateRuleRepository rules;

    public ChecklistTemplateMatcher(ChecklistTemplateRepository templates,
                                    ChecklistTemplateRuleRepository rules) {
        this.templates = templates;
        this.rules = rules;
    }

    /** One template's standing against an employee. */
    public record MatchOutcome(ChecklistTemplate template, int specificity, boolean isDefault) {}

    /**
     * Active templates for the flow that match the employee, best fit first.
     * Best = highest rule specificity, then highest priority, then code.
     */
    public List<MatchOutcome> rank(Employee emp, ChecklistFlowType flow) {
        List<MatchOutcome> matches = new ArrayList<>();
        for (ChecklistTemplate t : templates.findByFlowTypeAndActiveTrueOrderByName(flow)) {
            List<ChecklistTemplateRule> tplRules = rules.findByTemplateIdAndActiveTrue(t.getId());
            if (tplRules.isEmpty()) {
                matches.add(new MatchOutcome(t, 0, true));
                continue;
            }
            int best = -1;
            for (ChecklistTemplateRule r : tplRules) {
                if (ruleMatches(r, emp)) {
                    best = Math.max(best, r.specificity());
                }
            }
            if (best >= 0) {
                matches.add(new MatchOutcome(t, best, false));
            }
        }
        matches.sort(Comparator
                .comparingInt(MatchOutcome::specificity).reversed()
                .thenComparing((MatchOutcome m) -> m.template().getPriority(), Comparator.reverseOrder())
                .thenComparing(m -> m.template().getCode()));
        return matches;
    }

    /** The single best-fitting active template for the employee, if any. */
    public Optional<ChecklistTemplate> bestMatch(Employee emp, ChecklistFlowType flow) {
        List<MatchOutcome> ranked = rank(emp, flow);
        return ranked.isEmpty() ? Optional.empty() : Optional.of(ranked.get(0).template());
    }

    /** Every non-null filter on the rule must equal the employee's attribute. */
    private boolean ruleMatches(ChecklistTemplateRule r, Employee e) {
        if (r.getDepartmentName() != null
                && !r.getDepartmentName().equalsIgnoreCase(nullSafe(e.getDepartmentName()))) return false;
        if (r.getPositionId() != null && !r.getPositionId().equals(e.getPositionId())) return false;
        if (r.getOrgUnitId() != null && !r.getOrgUnitId().equals(e.getOrgUnitId())) return false;
        if (r.getWorkLocationId() != null && !r.getWorkLocationId().equals(e.getWorkLocationId())) return false;
        if (r.getEmploymentType() != null) {
            String empType = e.getEmploymentType() == null ? null : e.getEmploymentType().name();
            if (!r.getEmploymentType().equalsIgnoreCase(nullSafe(empType))) return false;
        }
        if (r.getEmployeeCategory() != null
                && !r.getEmployeeCategory().equalsIgnoreCase(nullSafe(e.getEmployeeCategory()))) return false;
        if (r.getNationality() != null
                && !r.getNationality().equalsIgnoreCase(nullSafe(e.getNationality()))) return false;
        return true;
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    /** UUID parse helper used by callers that only have the employeeId. */
    public static UUID asUuid(String s) {
        return (s == null || s.isBlank()) ? null : UUID.fromString(s);
    }
}
