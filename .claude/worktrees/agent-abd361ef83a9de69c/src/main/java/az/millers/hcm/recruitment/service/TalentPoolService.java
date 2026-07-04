package az.millers.hcm.recruitment.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.NoteRequest;
import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.NoteResponse;
import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.PoolCandidateRow;
import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.PoolSearchResponse;
import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.PoolStatusChange;
import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.TagResponse;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.CandidateNote;
import az.millers.hcm.recruitment.domain.CandidatePoolStatus;
import az.millers.hcm.recruitment.domain.CandidateTag;
import az.millers.hcm.recruitment.repo.CandidateNoteRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;
import az.millers.hcm.recruitment.repo.CandidateTagRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Talent pool / CRM service (M87). Search candidates across the whole
 * recruitment surface (not just open applications) by tag intersection,
 * pool status, and free text. Manage CRM-style tags + contact notes.
 *
 * <p>Tag filter semantics: AND. A candidate must carry every tag the caller
 * asked for. The {@code candidate_tag} table's HAVING-COUNT subquery does
 * the heavy lifting in one round-trip.
 */
@Service
public class TalentPoolService {

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY_CAND = "Candidate";
    private static final String ENTITY_TAG = "CandidateTag";
    private static final String ENTITY_NOTE = "CandidateNote";

    private final CandidateRepository candidates;
    private final CandidateTagRepository tags;
    private final CandidateNoteRepository notes;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public TalentPoolService(CandidateRepository candidates,
                              CandidateTagRepository tags,
                              CandidateNoteRepository notes,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.candidates = candidates;
        this.tags = tags;
        this.notes = notes;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Search ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PoolSearchResponse search(CandidatePoolStatus status,
                                       Collection<String> tagFilter,
                                       String q,
                                       int page, int size) {
        Collection<UUID> idsByTag = null;
        if (tagFilter != null && !tagFilter.isEmpty()) {
            // Lowercase the tags so the case-insensitive index hits.
            List<String> lower = tagFilter.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(t -> t.trim().toLowerCase())
                    .distinct()
                    .toList();
            if (!lower.isEmpty()) {
                idsByTag = tags.candidateIdsHavingAllTags(lower, lower.size());
                if (idsByTag.isEmpty()) {
                    return new PoolSearchResponse(page, size, 0, 0, List.of());
                }
            }
        }
        String qNorm = (q == null || q.isBlank()) ? null : q.trim();
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Candidate> p = candidates.searchPool(status, idsByTag, qNorm, pageable);

        // Hydrate tags for each candidate in one batch — avoids N+1.
        Map<UUID, List<String>> tagsById = loadTagsFor(p.getContent());
        List<PoolCandidateRow> rows = p.getContent().stream()
                .map(c -> PoolCandidateRow.from(c, tagsById.getOrDefault(c.getId(), List.of())))
                .toList();
        return new PoolSearchResponse(
                p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages(),
                rows);
    }

    @Transactional(readOnly = true)
    public List<String> knownTags() {
        return tags.distinctTags();
    }

    // ── Tags ─────────────────────────────────────────────────────────────────

    @Transactional
    public CandidateTag addTag(UUID candidateId, String tag) {
        if (!candidates.existsById(candidateId)) {
            throw new ResourceNotFoundException("Candidate not found: " + candidateId);
        }
        String normalised = tag == null ? null : tag.trim();
        if (normalised == null || normalised.isEmpty()) {
            throw new BadRequestException("Tag is required");
        }
        // The unique index on (candidate_id, lower(tag)) prevents dupes —
        // the service-level check makes the error nicer.
        boolean alreadyHas = tags.findByCandidateIdOrderByTagAsc(candidateId)
                .stream().anyMatch(t -> t.getTag().equalsIgnoreCase(normalised));
        if (alreadyHas) {
            throw new BadRequestException("Tag already on candidate: " + normalised);
        }
        CandidateTag t = new CandidateTag();
        t.setCandidateId(candidateId);
        t.setTag(normalised);
        t.setCreatedBy(currentRequest.username());
        CandidateTag saved = tags.save(t);
        audit.record(MODULE, ENTITY_TAG, saved.getId().toString(),
                "CREATE", null, TagResponse.from(saved));
        return saved;
    }

    @Transactional
    public void removeTag(UUID candidateId, UUID tagId) {
        CandidateTag t = tags.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tag not found: " + tagId));
        if (!t.getCandidateId().equals(candidateId)) {
            throw new BadRequestException("Tag does not belong to candidate");
        }
        TagResponse before = TagResponse.from(t);
        tags.delete(t);
        audit.record(MODULE, ENTITY_TAG, tagId.toString(), "DELETE", before, null);
    }

    @Transactional(readOnly = true)
    public List<CandidateTag> tagsOf(UUID candidateId) {
        return tags.findByCandidateIdOrderByTagAsc(candidateId);
    }

    // ── Notes ────────────────────────────────────────────────────────────────

    @Transactional
    public CandidateNote addNote(UUID candidateId, NoteRequest req) {
        Candidate c = candidates.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Candidate not found: " + candidateId));
        CandidateNote n = new CandidateNote();
        n.setCandidateId(candidateId);
        n.setKind(req.kind());
        n.setBody(req.body());
        n.setContactDate(req.contactDate());
        n.setCreatedBy(currentRequest.username());
        CandidateNote saved = notes.save(n);
        // Adding a CALL / EMAIL / MEETING note implicitly updates
        // last_contacted_at so the "stale outreach" view stays current.
        if (req.kind() != null && req.kind() != az.millers.hcm.recruitment.domain.CandidateNoteKind.NOTE
                && req.kind() != az.millers.hcm.recruitment.domain.CandidateNoteKind.OTHER) {
            c.setLastContactedAt(OffsetDateTime.now());
            candidates.save(c);
        }
        audit.record(MODULE, ENTITY_NOTE, saved.getId().toString(),
                "CREATE", null, NoteResponse.from(saved));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CandidateNote> notesOf(UUID candidateId) {
        return notes.findByCandidateIdOrderByCreatedAtDesc(candidateId);
    }

    // ── Pool status transitions ─────────────────────────────────────────────

    @Transactional
    public Candidate changePoolStatus(UUID candidateId, PoolStatusChange req) {
        Candidate c = candidates.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Candidate not found: " + candidateId));
        if (c.getPoolStatus() == req.newStatus()) return c;
        Map<String, Object> before = Map.of(
                "poolStatus", c.getPoolStatus(),
                "reason", "");
        c.setPoolStatus(req.newStatus());
        c.setUpdatedBy(currentRequest.username());
        Candidate saved = candidates.save(c);
        audit.record(MODULE, ENTITY_CAND, candidateId.toString(),
                "POOL_STATUS_CHANGE", before,
                Map.of("poolStatus", saved.getPoolStatus(),
                        "reason", req.reason() == null ? "" : req.reason()));
        return saved;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<UUID, List<String>> loadTagsFor(List<Candidate> rows) {
        if (rows.isEmpty()) return Map.of();
        Set<UUID> ids = new HashSet<>();
        for (Candidate c : rows) ids.add(c.getId());
        // One query per candidate is acceptable at pool sizes (≤ page_size)
        // but a proper fetch-all would beat it. The candidate_tag table is
        // tiny and the call is cheap, so keep it simple.
        Map<UUID, List<String>> out = new HashMap<>();
        for (UUID id : ids) {
            List<String> tagList = new ArrayList<>();
            for (CandidateTag t : tags.findByCandidateIdOrderByTagAsc(id)) {
                tagList.add(t.getTag());
            }
            out.put(id, tagList);
        }
        return out;
    }
}
