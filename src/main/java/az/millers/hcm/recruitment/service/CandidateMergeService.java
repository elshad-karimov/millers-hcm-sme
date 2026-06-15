package az.millers.hcm.recruitment.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.CandidateMergeDtos.DuplicateCandidate;
import az.millers.hcm.recruitment.api.dto.CandidateMergeDtos.DuplicateGroup;
import az.millers.hcm.recruitment.api.dto.CandidateMergeDtos.MergeResult;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.CandidatePoolStatus;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;

/**
 * M293 — Recruitment PRD §12: candidate duplicate detection + merge.
 *
 * <p>Detection groups live candidates by normalised email, phone, or
 * full name. Merge re-points every child record (applications, notes,
 * tags, education, experience, skills, documents) from the duplicate onto
 * the surviving master — skipping rows that would collide with a master
 * row it already has — fills the master's blank fields from the duplicate,
 * and retires the duplicate via {@code merged_into_id} (never deletes, so
 * history stays auditable).
 */
@Service
public class CandidateMergeService {

    private static final Logger log = LoggerFactory.getLogger(CandidateMergeService.class);
    private static final String MODULE = "RECRUITMENT";

    private final CandidateRepository candidates;
    private final ApplicationRepository applications;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public CandidateMergeService(CandidateRepository candidates,
                                  ApplicationRepository applications,
                                  NamedParameterJdbcTemplate jdbc,
                                  AuditService audit) {
        this.candidates = candidates;
        this.applications = applications;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // ── Detection ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DuplicateGroup> findDuplicates() {
        List<Candidate> live = candidates.findByMergedIntoIdIsNull();
        Map<String, List<Candidate>> byEmail = new LinkedHashMap<>();
        Map<String, List<Candidate>> byPhone = new LinkedHashMap<>();
        Map<String, List<Candidate>> byName = new LinkedHashMap<>();
        for (Candidate c : live) {
            String email = norm(c.getEmail());
            if (email != null) byEmail.computeIfAbsent(email, k -> new ArrayList<>()).add(c);
            String phone = digits(c.getPhone());
            if (phone != null && phone.length() >= 7) {
                byPhone.computeIfAbsent(phone, k -> new ArrayList<>()).add(c);
            }
            String name = norm((c.getFirstName() == null ? "" : c.getFirstName()) + " "
                    + (c.getLastName() == null ? "" : c.getLastName()));
            if (name != null && !name.isBlank()) {
                byName.computeIfAbsent(name, k -> new ArrayList<>()).add(c);
            }
        }

        List<DuplicateGroup> out = new ArrayList<>();
        Set<String> emitted = new HashSet<>(); // sorted-id-set keys already emitted
        // Strongest signal first; skip a weaker group whose exact member set
        // was already reported by a stronger one.
        addGroups(out, emitted, "EMAIL", byEmail);
        addGroups(out, emitted, "PHONE", byPhone);
        addGroups(out, emitted, "NAME", byName);
        return out;
    }

    private void addGroups(List<DuplicateGroup> out, Set<String> emitted,
                            String matchType, Map<String, List<Candidate>> grouped) {
        for (var e : grouped.entrySet()) {
            List<Candidate> members = e.getValue();
            if (members.size() < 2) continue;
            String key = members.stream().map(c -> c.getId().toString())
                    .collect(Collectors.toCollection(TreeSet::new)).toString();
            if (!emitted.add(key)) continue; // same set already reported by a stronger match
            out.add(new DuplicateGroup(matchType, e.getKey(),
                    members.stream().map(this::toDup).toList()));
        }
    }

    private DuplicateCandidate toDup(Candidate c) {
        long apps = applications.findByCandidateIdOrderByCreatedAtDesc(c.getId()).size();
        return new DuplicateCandidate(c.getId(), c.getCandidateNo(),
                ((c.getFirstName() == null ? "" : c.getFirstName()) + " "
                        + (c.getLastName() == null ? "" : c.getLastName())).trim(),
                c.getEmail(), c.getPhone(),
                c.getSource() == null ? null : c.getSource().name(),
                apps, c.getCreatedAt());
    }

    private static String norm(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase();
        return t.isEmpty() ? null : t;
    }

    private static String digits(String s) {
        if (s == null) return null;
        String d = s.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    // ── Merge ──────────────────────────────────────────────────────────

    @Transactional
    public MergeResult merge(UUID primaryId, UUID duplicateId) {
        if (primaryId.equals(duplicateId)) {
            throw new BadRequestException("Primary and duplicate must be different candidates");
        }
        Candidate primary = candidates.findById(primaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Primary candidate not found"));
        Candidate dup = candidates.findById(duplicateId)
                .orElseThrow(() -> new ResourceNotFoundException("Duplicate candidate not found"));
        if (primary.getMergedIntoId() != null) {
            throw new BadRequestException("Primary candidate has itself been merged away");
        }
        if (dup.getMergedIntoId() != null) {
            throw new BadRequestException("Duplicate candidate is already merged");
        }

        var p = new MapSqlParameterSource().addValue("pri", primaryId).addValue("dup", duplicateId);

        // 1) Drop duplicate rows that would collide with a master row it has.
        jdbc.update("""
                DELETE FROM recruitment.candidate_skill WHERE candidate_id = :dup
                  AND lower(name) IN (SELECT lower(name) FROM recruitment.candidate_skill WHERE candidate_id = :pri)
                """, p);
        jdbc.update("""
                DELETE FROM recruitment.candidate_document WHERE candidate_id = :dup
                  AND document_type_id IN (SELECT document_type_id FROM recruitment.candidate_document WHERE candidate_id = :pri)
                """, p);
        jdbc.update("""
                DELETE FROM recruitment.candidate_tag WHERE candidate_id = :dup
                  AND lower(tag) IN (SELECT lower(tag) FROM recruitment.candidate_tag WHERE candidate_id = :pri)
                """, p);

        // 2) Re-point the rest onto the master.
        int appsMoved = jdbc.update("""
                UPDATE recruitment.application SET candidate_id = :pri WHERE candidate_id = :dup
                  AND vacancy_id NOT IN (SELECT vacancy_id FROM recruitment.application WHERE candidate_id = :pri)
                """, p);
        int appsConflicted = jdbc.queryForObject(
                "SELECT count(*) FROM recruitment.application WHERE candidate_id = :dup",
                p, Integer.class);
        int notes = jdbc.update("UPDATE recruitment.candidate_note SET candidate_id = :pri WHERE candidate_id = :dup", p);
        int tags = jdbc.update("UPDATE recruitment.candidate_tag SET candidate_id = :pri WHERE candidate_id = :dup", p);
        int edu = jdbc.update("UPDATE recruitment.candidate_education SET candidate_id = :pri WHERE candidate_id = :dup", p);
        int exp = jdbc.update("UPDATE recruitment.candidate_experience SET candidate_id = :pri WHERE candidate_id = :dup", p);
        int skills = jdbc.update("UPDATE recruitment.candidate_skill SET candidate_id = :pri WHERE candidate_id = :dup", p);
        int docs = jdbc.update("UPDATE recruitment.candidate_document SET candidate_id = :pri WHERE candidate_id = :dup", p);

        // 3) Fill the master's blanks from the duplicate (don't overwrite).
        if (blank(primary.getEmail())) primary.setEmail(dup.getEmail());
        if (blank(primary.getPhone())) primary.setPhone(dup.getPhone());
        if (blank(primary.getMiddleName())) primary.setMiddleName(dup.getMiddleName());
        if (blank(primary.getCvUrl())) primary.setCvUrl(dup.getCvUrl());
        if (blank(primary.getSkills())) primary.setSkills(dup.getSkills());
        if (blank(primary.getNotes())) primary.setNotes(dup.getNotes());
        if (primary.getExperienceYears() == null) primary.setExperienceYears(dup.getExperienceYears());
        if (primary.getExpectedSalary() == null) primary.setExpectedSalary(dup.getExpectedSalary());
        if (primary.getSource() == null) primary.setSource(dup.getSource());
        if (primary.getConsentAt() == null) {
            primary.setConsentAt(dup.getConsentAt());
            primary.setConsentVersion(dup.getConsentVersion());
        }
        candidates.save(primary);

        // 4) Retire the duplicate.
        dup.setMergedIntoId(primaryId);
        dup.setPoolStatus(CandidatePoolStatus.ARCHIVED);
        candidates.save(dup);

        audit.record(MODULE, "Candidate", duplicateId.toString(), "MERGE", null,
                Map.of("mergedInto", primaryId.toString(),
                        "primaryNo", primary.getCandidateNo(),
                        "applicationsMoved", appsMoved,
                        "applicationsConflicted", appsConflicted));
        log.info("Merged candidate {} into {} ({} apps moved, {} conflicted)",
                dup.getCandidateNo(), primary.getCandidateNo(), appsMoved, appsConflicted);

        return new MergeResult(primaryId, duplicateId, appsMoved, appsConflicted,
                notes, tags, edu, exp, skills, docs);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
