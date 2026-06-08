package az.millers.hcm.policy.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.policy.api.dto.PolicyDtos.PolicyRequest;
import az.millers.hcm.policy.api.dto.PolicyDtos.PolicyResponse;
import az.millers.hcm.policy.api.dto.PolicyDtos.SelfPolicyView;
import az.millers.hcm.policy.domain.PolicyAcknowledgement;
import az.millers.hcm.policy.domain.PolicyBodyFormat;
import az.millers.hcm.policy.domain.PolicyDocument;
import az.millers.hcm.policy.domain.PolicyStatus;
import az.millers.hcm.policy.event.PolicyPublishedEvent;
import az.millers.hcm.policy.repo.PolicyAcknowledgementRepository;
import az.millers.hcm.policy.repo.PolicyDocumentRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M138 — admin CRUD + lifecycle transitions + the self-service browse.
 *
 * <p>Lifecycle rules:
 * <ul>
 *   <li>Create → always lands as DRAFT, version monotonically increments
 *       over previous versions of the same {@code code}.</li>
 *   <li>Update → only DRAFT rows are mutable.</li>
 *   <li>PUBLISHED rows can only transition to ARCHIVED. New content
 *       requires a new draft (next version).</li>
 * </ul>
 */
@Service
public class PolicyService {

    private static final String MODULE = "POLICY";
    private static final String ENTITY = "PolicyDocument";

    private final PolicyDocumentRepository policies;
    private final PolicyAcknowledgementRepository acks;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final EmployeeContextService employeeContext;
    private final ApplicationEventPublisher eventPublisher;

    public PolicyService(PolicyDocumentRepository policies,
                          PolicyAcknowledgementRepository acks,
                          AuditService audit,
                          CurrentRequest currentRequest,
                          EmployeeContextService employeeContext,
                          ApplicationEventPublisher eventPublisher) {
        this.policies = policies;
        this.acks = acks;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.employeeContext = employeeContext;
        this.eventPublisher = eventPublisher;
    }

    // ── Admin CRUD ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PolicyDocument> list(PolicyStatus status) {
        return status == null
                ? policies.findAll()
                : policies.findByStatusOrderByCategoryAscTitleAsc(status);
    }

    @Transactional(readOnly = true)
    public PolicyDocument get(UUID id) {
        return policies.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Policy not found: " + id));
    }

    @Transactional
    public PolicyDocument create(PolicyRequest req) {
        validateBodyPayload(req);
        PolicyDocument p = new PolicyDocument();
        apply(p, req);
        // Bump version above the latest draft / published row for the
        // same code, if any.
        int nextVersion = policies.findTopByCodeOrderByVersionDesc(req.code())
                .map(prev -> prev.getVersion() + 1)
                .orElse(1);
        p.setVersion(nextVersion);
        p.setStatus(PolicyStatus.DRAFT);
        p.setCreatedBy(currentRequest.username());
        p.setUpdatedBy(currentRequest.username());
        PolicyDocument saved = policies.save(p);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, PolicyResponse.from(saved));
        return saved;
    }

    @Transactional
    public PolicyDocument update(UUID id, PolicyRequest req) {
        PolicyDocument p = get(id);
        if (p.getStatus() != PolicyStatus.DRAFT) {
            throw new BadRequestException(
                    "Only DRAFT policies can be edited; create a new version to change " + p.getStatus());
        }
        validateBodyPayload(req);
        PolicyResponse before = PolicyResponse.from(p);
        apply(p, req);
        p.setUpdatedBy(currentRequest.username());
        PolicyDocument saved = policies.save(p);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, PolicyResponse.from(saved));
        return saved;
    }

    @Transactional
    public PolicyDocument changeStatus(UUID id, PolicyStatus target) {
        PolicyDocument p = get(id);
        PolicyStatus from = p.getStatus();
        boolean allowed = (from == PolicyStatus.DRAFT && target == PolicyStatus.PUBLISHED)
                || (from == PolicyStatus.PUBLISHED && target == PolicyStatus.ARCHIVED);
        if (!allowed) {
            throw new BadRequestException(
                    "Status transition " + from + " → " + target + " is not allowed");
        }
        PolicyResponse before = PolicyResponse.from(p);
        p.setStatus(target);
        p.setUpdatedBy(currentRequest.username());
        PolicyDocument saved = policies.save(p);
        audit.record(MODULE, ENTITY, id.toString(),
                "STATUS_CHANGE", before, PolicyResponse.from(saved));

        // Notify all ACTIVE employees when a mandatory-ack policy is published.
        if (target == PolicyStatus.PUBLISHED && saved.isRequiresAck()) {
            eventPublisher.publishEvent(new PolicyPublishedEvent(
                    saved.getId(), saved.getCode(), saved.getTitle(), saved.getVersion()));
        }
        return saved;
    }

    // ── Self-service browse ────────────────────────────────────────────

    /**
     * Returns every PUBLISHED policy in the catalogue with a derived
     * {@code acknowledged} flag scoped to the caller. Sorted by category
     * then title (matches the admin list).
     */
    @Transactional(readOnly = true)
    public List<SelfPolicyView> browseForCurrent() {
        UUID employeeId = employeeContext.currentEmployee().getId();
        List<PolicyDocument> published = policies.findByStatusOrderByCategoryAscTitleAsc(
                PolicyStatus.PUBLISHED);
        if (published.isEmpty()) return List.of();
        Set<UUID> ids = new HashSet<>(published.size());
        for (PolicyDocument p : published) ids.add(p.getId());
        Map<UUID, PolicyAcknowledgement> byPolicy = new HashMap<>();
        for (PolicyAcknowledgement a : acks.findByEmployeeIdAndPolicyIdIn(employeeId, ids)) {
            byPolicy.put(a.getPolicyId(), a);
        }
        List<SelfPolicyView> out = new ArrayList<>(published.size());
        for (PolicyDocument p : published) {
            PolicyAcknowledgement a = byPolicy.get(p.getId());
            out.add(new SelfPolicyView(
                    PolicyResponse.from(p),
                    a != null,
                    a == null ? null : a.getAcknowledgedAt()));
        }
        return out;
    }

    @Transactional
    public PolicyAcknowledgement acknowledge(UUID policyId) {
        PolicyDocument p = get(policyId);
        if (p.getStatus() != PolicyStatus.PUBLISHED) {
            throw new BadRequestException("Cannot acknowledge a non-published policy");
        }
        UUID employeeId = employeeContext.currentEmployee().getId();
        // Idempotent — if the employee already acknowledged this version,
        // return the existing row unchanged.
        return acks.findByPolicyIdAndEmployeeId(policyId, employeeId)
                .orElseGet(() -> {
                    PolicyAcknowledgement a = new PolicyAcknowledgement();
                    a.setPolicyId(policyId);
                    a.setEmployeeId(employeeId);
                    a.setVersionAcknowledged(p.getVersion());
                    a.setAcknowledgedIp(currentRequest.ipAddress());
                    PolicyAcknowledgement saved = acks.save(a);
                    audit.record(MODULE, "PolicyAcknowledgement", saved.getId().toString(),
                            "ACKNOWLEDGE", null,
                            az.millers.hcm.policy.api.dto.PolicyDtos.AcknowledgementResponse.from(saved));
                    return saved;
                });
    }

    @Transactional(readOnly = true)
    public List<PolicyAcknowledgement> acknowledgementsFor(UUID policyId) {
        return acks.findByPolicyId(policyId);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static void apply(PolicyDocument p, PolicyRequest req) {
        p.setCode(req.code());
        p.setTitle(req.title());
        p.setSummary(req.summary());
        p.setCategory(req.category());
        p.setBodyFormat(req.bodyFormat());
        p.setBodyText(req.bodyText());
        p.setAttachmentUrl(req.attachmentUrl());
        p.setEffectiveFrom(req.effectiveFrom());
        p.setEffectiveTo(req.effectiveTo());
        p.setRequiresAck(req.requiresAck());
    }

    /**
     * Inline body (MARKDOWN/HTML) requires {@code bodyText}; URL/PDF
     * require {@code attachmentUrl}. Mirrored as a SQL CHECK would force
     * cross-column logic; service-layer keeps the contract clear.
     */
    private static void validateBodyPayload(PolicyRequest req) {
        boolean needsText = req.bodyFormat() == PolicyBodyFormat.MARKDOWN
                || req.bodyFormat() == PolicyBodyFormat.HTML;
        boolean needsUrl = req.bodyFormat() == PolicyBodyFormat.PDF
                || req.bodyFormat() == PolicyBodyFormat.URL;
        if (needsText && (req.bodyText() == null || req.bodyText().isBlank())) {
            throw new BadRequestException(
                    "bodyText is required for body format " + req.bodyFormat());
        }
        if (needsUrl && (req.attachmentUrl() == null || req.attachmentUrl().isBlank())) {
            throw new BadRequestException(
                    "attachmentUrl is required for body format " + req.bodyFormat());
        }
    }
}
