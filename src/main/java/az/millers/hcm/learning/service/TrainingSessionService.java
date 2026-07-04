package az.millers.hcm.learning.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.learning.domain.Instructor;
import az.millers.hcm.learning.domain.TrainingAttendance;
import az.millers.hcm.learning.domain.TrainingRoom;
import az.millers.hcm.learning.domain.TrainingSession;
import az.millers.hcm.learning.repo.CourseRepository;
import az.millers.hcm.learning.repo.InstructorRepository;
import az.millers.hcm.learning.repo.TrainingAttendanceRepository;
import az.millers.hcm.learning.repo.TrainingRoomRepository;
import az.millers.hcm.learning.repo.TrainingSessionRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_14 M404/M405 — instructors, rooms, classroom sessions and attendance
 * (PRD 14 §7/§11/§18). Completing a session finalises open attendance
 * (ENROLLED → NO_SHOW); the LMS e-enrolment completion bridge stays a seam.
 */
@Service
public class TrainingSessionService {

    private static final String TENANT = "default";
    private static final String MODULE = "LEARNING";
    private static final List<String> LIVE_ATTENDANCE = List.of("ENROLLED", "ATTENDED", "LATE");
    private static final Set<String> ATTENDANCE_STATUSES =
            Set.of("ENROLLED", "ATTENDED", "LATE", "NO_SHOW", "CANCELLED");

    public record SessionRequest(UUID courseId, UUID instructorId, UUID roomId,
                                 OffsetDateTime startAt, OffsetDateTime endAt,
                                 Integer capacity, String notes) {}

    public record InstructorRequest(UUID employeeId, String externalName, String email,
                                    String qualifications, java.math.BigDecimal hourlyCost,
                                    Boolean active) {}

    public record RoomRequest(String code, String name, String location, Integer capacity,
                              Boolean virtual, String notes, Boolean active) {}

    private final TrainingSessionRepository sessions;
    private final TrainingAttendanceRepository attendance;
    private final InstructorRepository instructors;
    private final TrainingRoomRepository rooms;
    private final CourseRepository courses;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public TrainingSessionService(TrainingSessionRepository sessions,
                                  TrainingAttendanceRepository attendance,
                                  InstructorRepository instructors,
                                  TrainingRoomRepository rooms,
                                  CourseRepository courses,
                                  EmployeeRepository employees,
                                  AuditService audit,
                                  CurrentRequest currentRequest) {
        this.sessions = sessions;
        this.attendance = attendance;
        this.instructors = instructors;
        this.rooms = rooms;
        this.courses = courses;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Instructors (M404) ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Instructor> listInstructors(boolean activeOnly) {
        return activeOnly
                ? instructors.findByTenantIdAndActiveTrueOrderByCreatedAtAsc(TENANT)
                : instructors.findByTenantIdOrderByCreatedAtAsc(TENANT);
    }

    @Transactional
    public Instructor saveInstructor(UUID id, InstructorRequest req) {
        if (req.employeeId() == null && (req.externalName() == null || req.externalName().isBlank())) {
            throw new BadRequestException("An instructor is an employee or an external name");
        }
        if (req.employeeId() != null && !employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        Instructor i = id == null ? new Instructor()
                : instructors.findById(id).orElseThrow(
                        () -> new ResourceNotFoundException("Instructor not found: " + id));
        i.setTenantId(TENANT);
        i.setEmployeeId(req.employeeId());
        i.setExternalName(req.externalName());
        i.setEmail(req.email());
        i.setQualifications(req.qualifications());
        i.setHourlyCost(req.hourlyCost());
        if (req.active() != null) i.setActive(req.active());
        Instructor saved = instructors.save(i);
        audit.record(MODULE, "Instructor", saved.getId().toString(),
                id == null ? "CREATE" : "UPDATE", null,
                req.employeeId() != null ? req.employeeId().toString() : req.externalName());
        return saved;
    }

    // ── Rooms (M404) ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TrainingRoom> listRooms() {
        return rooms.findByTenantIdOrderByCodeAsc(TENANT);
    }

    @Transactional
    public TrainingRoom saveRoom(UUID id, RoomRequest req) {
        if (req.code() == null || req.code().isBlank()
                || req.name() == null || req.name().isBlank()) {
            throw new BadRequestException("Room code and name are required");
        }
        String code = req.code().trim().toUpperCase();
        TrainingRoom r;
        if (id == null) {
            if (rooms.existsByTenantIdAndCode(TENANT, code)) {
                throw new BadRequestException("Room code already exists: " + code);
            }
            r = new TrainingRoom();
            r.setTenantId(TENANT);
        } else {
            r = rooms.findById(id).orElseThrow(
                    () -> new ResourceNotFoundException("Room not found: " + id));
        }
        r.setCode(code);
        r.setName(req.name().trim());
        r.setLocation(req.location());
        r.setCapacity(req.capacity());
        if (req.virtual() != null) r.setVirtual(req.virtual());
        r.setNotes(req.notes());
        if (req.active() != null) r.setActive(req.active());
        TrainingRoom saved = rooms.save(r);
        audit.record(MODULE, "TrainingRoom", saved.getId().toString(),
                id == null ? "CREATE" : "UPDATE", null, code);
        return saved;
    }

    // ── Sessions (M405) ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TrainingSession> listSessions(UUID courseId, String status) {
        if (courseId != null) {
            return sessions.findByTenantIdAndCourseIdOrderByStartAtDesc(TENANT, courseId);
        }
        if (status != null) {
            return sessions.findByTenantIdAndStatusOrderByStartAtAsc(TENANT, status);
        }
        return sessions.findByTenantIdOrderByStartAtDesc(TENANT);
    }

    @Transactional
    public TrainingSession saveSession(UUID id, SessionRequest req) {
        if (req.courseId() == null || !courses.existsById(req.courseId())) {
            throw new BadRequestException("Course not found: " + req.courseId());
        }
        if (req.startAt() == null || req.endAt() == null || !req.endAt().isAfter(req.startAt())) {
            throw new BadRequestException("Session end must be after start");
        }
        if (req.instructorId() != null && !instructors.existsById(req.instructorId())) {
            throw new BadRequestException("Instructor not found: " + req.instructorId());
        }
        if (req.roomId() != null && !rooms.existsById(req.roomId())) {
            throw new BadRequestException("Room not found: " + req.roomId());
        }
        TrainingSession s;
        if (id == null) {
            s = new TrainingSession();
            s.setTenantId(TENANT);
            s.setCreatedBy(currentRequest.username());
        } else {
            s = requireSession(id);
            if (!"SCHEDULED".equals(s.getStatus())) {
                throw new BadRequestException("Only SCHEDULED sessions can be edited");
            }
        }
        s.setCourseId(req.courseId());
        s.setInstructorId(req.instructorId());
        s.setRoomId(req.roomId());
        s.setStartAt(req.startAt());
        s.setEndAt(req.endAt());
        s.setCapacity(req.capacity());
        s.setNotes(req.notes());
        TrainingSession saved = sessions.save(s);
        audit.record(MODULE, "TrainingSession", saved.getId().toString(),
                id == null ? "CREATE" : "UPDATE", null,
                Map.of("courseId", req.courseId().toString(), "startAt", req.startAt().toString()));
        return saved;
    }

    @Transactional
    public TrainingSession changeSessionStatus(UUID id, String status) {
        if (!Set.of("SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED").contains(status)) {
            throw new BadRequestException("Unknown session status: " + status);
        }
        TrainingSession s = requireSession(id);
        String before = s.getStatus();
        if ("COMPLETED".equals(status)) {
            // §11 — finalise open attendance: anyone still ENROLLED did not show up.
            for (TrainingAttendance a : attendance.findBySessionIdOrderByCreatedAtAsc(id)) {
                if ("ENROLLED".equals(a.getStatus())) {
                    a.setStatus("NO_SHOW");
                    attendance.save(a);
                }
            }
        }
        s.setStatus(status);
        TrainingSession saved = sessions.save(s);
        audit.record(MODULE, "TrainingSession", id.toString(), "STATUS", before, status);
        return saved;
    }

    // ── Attendance (M405) ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TrainingAttendance> attendanceFor(UUID sessionId) {
        requireSession(sessionId);
        return attendance.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public TrainingAttendance enrol(UUID sessionId, UUID employeeId) {
        TrainingSession s = requireSession(sessionId);
        if (!"SCHEDULED".equals(s.getStatus()) && !"IN_PROGRESS".equals(s.getStatus())) {
            throw new BadRequestException("Enrolment requires a SCHEDULED/IN_PROGRESS session");
        }
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        if (attendance.existsBySessionIdAndEmployeeId(sessionId, employeeId)) {
            throw new BadRequestException("Employee is already enrolled in this session");
        }
        Integer cap = s.getCapacity();
        if (cap == null && s.getRoomId() != null) {
            cap = rooms.findById(s.getRoomId()).map(TrainingRoom::getCapacity).orElse(null);
        }
        if (cap != null) {
            long live = attendance.countBySessionIdAndStatusIn(sessionId, LIVE_ATTENDANCE);
            if (live >= cap) {
                throw new BadRequestException("Session is full (capacity " + cap + ")");
            }
        }
        TrainingAttendance a = new TrainingAttendance();
        a.setTenantId(TENANT);
        a.setSessionId(sessionId);
        a.setEmployeeId(employeeId);
        TrainingAttendance saved = attendance.save(a);
        audit.record(MODULE, "TrainingAttendance", saved.getId().toString(), "ENROL",
                null, employeeId.toString());
        return saved;
    }

    @Transactional
    public TrainingAttendance mark(UUID attendanceId, String status, String note) {
        if (!ATTENDANCE_STATUSES.contains(status)) {
            throw new BadRequestException("Unknown attendance status: " + status);
        }
        TrainingAttendance a = attendance.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance not found: " + attendanceId));
        String before = a.getStatus();
        a.setStatus(status);
        if (note != null) a.setNote(note);
        if (("ATTENDED".equals(status) || "LATE".equals(status)) && a.getCheckedInAt() == null) {
            a.setCheckedInAt(OffsetDateTime.now());
        }
        TrainingAttendance saved = attendance.save(a);
        audit.record(MODULE, "TrainingAttendance", attendanceId.toString(), "MARK", before, status);
        return saved;
    }

    private TrainingSession requireSession(UUID id) {
        return sessions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Training session not found: " + id));
    }
}
