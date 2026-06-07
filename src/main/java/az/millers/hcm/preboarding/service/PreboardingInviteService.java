package az.millers.hcm.preboarding.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.DependentRequest;
import az.millers.hcm.corehr.api.dto.EmergencyContactRequest;
import az.millers.hcm.corehr.domain.DependentRelationship;
import az.millers.hcm.corehr.domain.EmergencyRelationship;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.MaritalStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.corehr.service.EmployeeDependentService;
import az.millers.hcm.corehr.service.EmployeeEmergencyContactService;
import az.millers.hcm.preboarding.api.PreboardingDtos.CompleteRequest;
import az.millers.hcm.preboarding.api.PreboardingDtos.InviteDetail;
import az.millers.hcm.preboarding.api.PreboardingDtos.InviteSummary;
import az.millers.hcm.preboarding.api.PreboardingDtos.IssueRequest;
import az.millers.hcm.preboarding.api.PreboardingDtos.IssueResponse;
import az.millers.hcm.preboarding.api.PreboardingDtos.PublicInfo;
import az.millers.hcm.preboarding.api.PreboardingDtos.SubmitResponse;
import az.millers.hcm.preboarding.domain.PreboardingInvite;
import az.millers.hcm.preboarding.domain.PreboardingStatus;
import az.millers.hcm.preboarding.repo.PreboardingInviteRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M122 — orchestrates the pre-boarding invite lifecycle.
 *
 * <p>HR-side: issue, revoke, complete, list. Public-side: resolveByToken
 * (returns null on any rejection — never throws so the public endpoint
 * can map cleanly to 404), markOpened (idempotent), submit. The
 * grad-to-Employee logic on complete delegates to the existing
 * EmployeeService / EmergencyContact / Dependent services so per-field
 * validation is reused — no business logic duplicated.
 */
@Service
public class PreboardingInviteService {

    private static final int DEFAULT_EXPIRY_DAYS = 14;

    private final PreboardingInviteRepository repo;
    private final EmployeeRepository employees;
    private final EmployeeEmergencyContactService emergencyContacts;
    private final EmployeeDependentService dependents;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PreboardingInviteService(PreboardingInviteRepository repo,
                                    EmployeeRepository employees,
                                    EmployeeEmergencyContactService emergencyContacts,
                                    EmployeeDependentService dependents,
                                    AuditService audit,
                                    CurrentRequest currentRequest) {
        this.repo = repo;
        this.employees = employees;
        this.emergencyContacts = emergencyContacts;
        this.dependents = dependents;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Issue / revoke ──────────────────────────────────────────────────────

    @Transactional
    public IssueResponse issue(IssueRequest req) {
        if (req.employeeId() == null) {
            throw new BadRequestException("employeeId is required");
        }
        Employee employee = employees.findById(req.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + req.employeeId()));
        int days = req.expiresInDays() == null ? DEFAULT_EXPIRY_DAYS : req.expiresInDays();
        if (days < 1 || days > 90) {
            throw new BadRequestException("expiresInDays must be between 1 and 90");
        }

        String plaintext = PreboardingTokens.generatePlaintext();
        PreboardingInvite invite = new PreboardingInvite();
        invite.setEmployeeId(employee.getId());
        invite.setTokenHash(PreboardingTokens.hash(plaintext));
        invite.setStatus(PreboardingStatus.SENT);
        invite.setExpiresAt(OffsetDateTime.now().plusDays(days));
        invite.setSentAt(OffsetDateTime.now());
        invite.setNote(req.note());
        invite.setCreatedBy(currentRequest.username());
        PreboardingInvite saved = repo.save(invite);

        audit.record("corehr", "PreboardingInvite", saved.getId().toString(), "ISSUE",
                null,
                Map.of(
                    "employeeId", employee.getId().toString(),
                    "employeeNo", employee.getEmployeeNo(),
                    "expiresAt", saved.getExpiresAt(),
                    "status", saved.getStatus()));
        return new IssueResponse(toSummary(saved, employee), plaintext);
    }

    @Transactional
    public InviteSummary revoke(UUID id, String reason) {
        PreboardingInvite invite = findOrThrow(id);
        if (invite.getStatus().isTerminal()) {
            throw new BadRequestException(
                    "Cannot revoke an invite in " + invite.getStatus() + " state");
        }
        Map<String, Object> before = Map.of("status", invite.getStatus());
        invite.setStatus(PreboardingStatus.REVOKED);
        invite.setRevokedAt(OffsetDateTime.now());
        invite.setRevokedBy(currentRequest.username());
        invite.setRevokeReason(reason);
        PreboardingInvite saved = repo.save(invite);
        audit.record("corehr", "PreboardingInvite", id.toString(), "REVOKE",
                before, Map.of("status", saved.getStatus(), "reason", reason == null ? "" : reason));
        Employee employee = employees.findById(invite.getEmployeeId()).orElse(null);
        return toSummary(saved, employee);
    }

    // ── Read ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InviteSummary> listAll() {
        List<PreboardingInvite> all = repo.findAllByOrderByCreatedAtDesc();
        Map<UUID, Employee> byId = loadEmployeesFor(all);
        return all.stream().map(i -> toSummary(i, byId.get(i.getEmployeeId()))).toList();
    }

    @Transactional(readOnly = true)
    public InviteDetail getDetail(UUID id) {
        PreboardingInvite invite = findOrThrow(id);
        Employee employee = employees.findById(invite.getEmployeeId()).orElse(null);
        return new InviteDetail(toSummary(invite, employee), invite.getPayloadJson());
    }

    // ── Public (candidate-facing) ───────────────────────────────────────────

    /**
     * Plaintext → invite. Returns {@code null} for any rejection
     * (unknown token, expired, revoked, completed, malformed). The
     * controller maps to 404 / 410 as appropriate.
     */
    @Transactional(readOnly = true)
    public PreboardingInvite resolveByToken(String plaintext) {
        if (!PreboardingTokens.looksValid(plaintext)) return null;
        return repo.findByTokenHash(PreboardingTokens.hash(plaintext))
                .filter(i -> i.isAccessibleToCandidate(OffsetDateTime.now()))
                .orElse(null);
    }

    /** Bump status to OPENED on first GET /info. Idempotent. */
    @Transactional
    public void markOpenedIfNeeded(PreboardingInvite invite) {
        if (invite.getStatus() == PreboardingStatus.SENT) {
            invite.setStatus(PreboardingStatus.OPENED);
            invite.setOpenedAt(OffsetDateTime.now());
            repo.save(invite);
        }
    }

    @Transactional
    public SubmitResponse submit(PreboardingInvite invite, Map<String, Object> payload) {
        PreboardingPayload.validate(payload);
        Map<String, Object> before = Map.of(
                "status", invite.getStatus(),
                "hadPayload", invite.getPayloadJson() != null);
        invite.setPayloadJson(payload);
        invite.setStatus(PreboardingStatus.SUBMITTED);
        invite.setSubmittedAt(OffsetDateTime.now());
        PreboardingInvite saved = repo.save(invite);
        // Audit on the candidate side — no JWT, so actor is "candidate-token".
        audit.record("corehr", "PreboardingInvite", saved.getId().toString(),
                "SUBMIT", before, Map.of("status", saved.getStatus()));
        return new SubmitResponse(saved.getStatus(), saved.getSubmittedAt());
    }

    public PublicInfo publicInfo(PreboardingInvite invite, Employee employee) {
        return new PublicInfo(
                employee == null
                        ? "—"
                        : (employee.getFirstName() + " " + employee.getLastName()),
                employee == null ? "—" : employee.getEmployeeNo(),
                invite.getStatus(),
                invite.getExpiresAt(),
                PreboardingFormSchema.SCHEMA);
    }

    public Optional<Employee> loadEmployee(PreboardingInvite invite) {
        return employees.findById(invite.getEmployeeId());
    }

    // ── HR: graduate the payload into core_hr ──────────────────────────────

    /**
     * Apply the captured payload to the Employee record and create
     * EmergencyContact / Dependent rows. Returns a counts summary the
     * SPA can show ("Updated 5 personal fields, created 2 emergency
     * contacts, 1 dependent").
     */
    @Transactional
    public InviteSummary complete(UUID id, CompleteRequest req) {
        PreboardingInvite invite = findOrThrow(id);
        if (invite.getStatus() != PreboardingStatus.SUBMITTED) {
            throw new BadRequestException(
                    "Invite must be SUBMITTED to complete (was " + invite.getStatus() + ")");
        }
        Map<String, Object> payload = invite.getPayloadJson();
        if (payload == null) {
            throw new BadRequestException("Invite has no payload — submit first");
        }
        Employee employee = employees.findById(invite.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee no longer exists: " + invite.getEmployeeId()));

        boolean takePersonal = req == null || req.personalInfo() == null || req.personalInfo();
        boolean takeEmergency = req == null || req.emergencyContacts() == null || req.emergencyContacts();
        boolean takeDependents = req == null || req.dependents() == null || req.dependents();

        if (takePersonal) {
            applyPersonalInfo(employee, asMap(payload.get("personalInfo")));
            employees.save(employee);
        }
        int emergencyCount = 0;
        if (takeEmergency) emergencyCount = applyEmergencyContacts(employee.getId(),
                asListOfMaps(payload.get("emergencyContacts")));
        int dependentCount = 0;
        if (takeDependents) dependentCount = applyDependents(employee.getId(),
                asListOfMaps(payload.get("dependents")));

        Map<String, Object> before = Map.of("status", invite.getStatus());
        invite.setStatus(PreboardingStatus.COMPLETED);
        invite.setCompletedAt(OffsetDateTime.now());
        invite.setCompletedBy(currentRequest.username());
        if (req != null && req.note() != null) invite.setNote(req.note());
        PreboardingInvite saved = repo.save(invite);

        audit.record("corehr", "PreboardingInvite", id.toString(), "COMPLETE",
                before,
                Map.of(
                    "status", saved.getStatus(),
                    "completedBy", saved.getCompletedBy(),
                    "emergencyContactsCreated", emergencyCount,
                    "dependentsCreated", dependentCount));
        return toSummary(saved, employee);
    }

    private void applyPersonalInfo(Employee e, Map<String, Object> p) {
        if (p == null) return;
        String phone = stringOrNull(p.get("phone"));
        if (phone != null) e.setPhone(phone);
        String personalEmail = stringOrNull(p.get("personalEmail"));
        if (personalEmail != null && e.getEmail() == null) e.setEmail(personalEmail);
        String birth = stringOrNull(p.get("birthDate"));
        if (birth != null) e.setBirthDate(LocalDate.parse(birth));
        String gender = stringOrNull(p.get("gender"));
        if (gender != null) e.setGender(gender);
        String marital = stringOrNull(p.get("maritalStatus"));
        if (marital != null) {
            try { e.setMaritalStatus(MaritalStatus.valueOf(marital.toUpperCase())); }
            catch (IllegalArgumentException ex) {
                throw new BadRequestException("Unknown maritalStatus: " + marital);
            }
        }
        String nationality = stringOrNull(p.get("nationality"));
        if (nationality != null) e.setNationality(nationality);
        String nationalId = stringOrNull(p.get("nationalId"));
        if (nationalId != null) e.setNationalId(nationalId);
    }

    private int applyEmergencyContacts(UUID employeeId, List<Map<String, Object>> rows) {
        if (rows == null) return 0;
        int n = 0;
        for (Map<String, Object> row : rows) {
            EmergencyRelationship rel;
            try {
                rel = EmergencyRelationship.valueOf(
                        String.valueOf(row.get("relationship")).toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException(
                        "Unknown emergencyContacts[].relationship: " + row.get("relationship"));
            }
            EmergencyContactRequest dto = new EmergencyContactRequest(
                    stringOrNull(row.get("name")),
                    rel,
                    stringOrNull(row.get("phone")),
                    stringOrNull(row.get("altPhone")),
                    stringOrNull(row.get("email")),
                    stringOrNull(row.get("address")),
                    asBoolean(row.get("primary"), false),
                    null);
            emergencyContacts.create(employeeId, dto);
            n++;
        }
        return n;
    }

    private int applyDependents(UUID employeeId, List<Map<String, Object>> rows) {
        if (rows == null) return 0;
        int n = 0;
        for (Map<String, Object> row : rows) {
            DependentRelationship rel;
            try {
                rel = DependentRelationship.valueOf(
                        String.valueOf(row.get("relationshipType")).toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException(
                        "Unknown dependents[].relationshipType: " + row.get("relationshipType"));
            }
            String dobStr = stringOrNull(row.get("dateOfBirth"));
            LocalDate dob = dobStr == null ? null : LocalDate.parse(dobStr);
            DependentRequest dto = new DependentRequest(
                    rel,
                    stringOrNull(row.get("firstName")),
                    stringOrNull(row.get("lastName")),
                    stringOrNull(row.get("middleName")),
                    dob,
                    stringOrNull(row.get("gender")),
                    stringOrNull(row.get("nationalId")),
                    stringOrNull(row.get("phone")),
                    stringOrNull(row.get("email")),
                    asBoolean(row.get("insuranceEligible"), false),
                    asBoolean(row.get("benefitEligible"), false),
                    Boolean.TRUE,
                    // M135 — new hires arrive eligible; pre-boarding doesn't
                    // capture eligibility-end metadata.
                    null, null,
                    stringOrNull(row.get("notes")));
            dependents.create(employeeId, dto);
            n++;
        }
        return n;
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private PreboardingInvite findOrThrow(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found: " + id));
    }

    private Map<UUID, Employee> loadEmployeesFor(List<PreboardingInvite> invites) {
        List<UUID> ids = new ArrayList<>();
        for (PreboardingInvite i : invites) ids.add(i.getEmployeeId());
        if (ids.isEmpty()) return Map.of();
        Map<UUID, Employee> byId = new HashMap<>();
        for (Employee e : employees.findAllById(ids)) byId.put(e.getId(), e);
        return byId;
    }

    private static InviteSummary toSummary(PreboardingInvite i, Employee e) {
        return new InviteSummary(
                i.getId(),
                i.getEmployeeId(),
                e == null ? "—" : e.getFirstName() + " " + e.getLastName(),
                e == null ? "—" : e.getEmployeeNo(),
                i.getStatus(),
                i.getExpiresAt(),
                i.getSentAt(),
                i.getOpenedAt(),
                i.getSubmittedAt(),
                i.getCompletedAt(),
                i.getCompletedBy(),
                i.getRevokedAt(),
                i.getRevokedBy(),
                i.getRevokeReason(),
                i.getCreatedAt(),
                i.getCreatedBy(),
                i.getNote());
    }

    private static String stringOrNull(Object o) {
        if (o == null) return null;
        String s = o.toString();
        return s.isBlank() ? null : s;
    }

    private static Boolean asBoolean(Object o, boolean dflt) {
        if (o == null) return dflt;
        if (o instanceof Boolean b) return b;
        return Boolean.parseBoolean(o.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object o) {
        if (!(o instanceof List<?> l)) return null;
        List<Map<String, Object>> out = new ArrayList<>(l.size());
        for (Object item : l) if (item instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        return out;
    }
}
