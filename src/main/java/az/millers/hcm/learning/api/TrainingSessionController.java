package az.millers.hcm.learning.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.learning.domain.Instructor;
import az.millers.hcm.learning.domain.TrainingAttendance;
import az.millers.hcm.learning.domain.TrainingRoom;
import az.millers.hcm.learning.domain.TrainingSession;
import az.millers.hcm.learning.service.TrainingSessionService;
import az.millers.hcm.security.SecurityRoles;

/** Classroom training API — instructors, rooms, sessions, attendance (M404/M405). */
@RestController
@RequestMapping("/api/learning")
public class TrainingSessionController {

    private final TrainingSessionService service;

    public TrainingSessionController(TrainingSessionService service) {
        this.service = service;
    }

    // ── Instructors ─────────────────────────────────────────────────────────

    @GetMapping("/instructors")
    @PreAuthorize("isAuthenticated()")
    public List<Instructor> instructors(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.listInstructors(activeOnly);
    }

    @PostMapping("/instructors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public Instructor createInstructor(@RequestBody TrainingSessionService.InstructorRequest req) {
        return service.saveInstructor(null, req);
    }

    @PutMapping("/instructors/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public Instructor updateInstructor(@PathVariable UUID id,
                                       @RequestBody TrainingSessionService.InstructorRequest req) {
        return service.saveInstructor(id, req);
    }

    // ── Rooms ───────────────────────────────────────────────────────────────

    @GetMapping("/training-rooms")
    @PreAuthorize("isAuthenticated()")
    public List<TrainingRoom> rooms() {
        return service.listRooms();
    }

    @PostMapping("/training-rooms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingRoom createRoom(@RequestBody TrainingSessionService.RoomRequest req) {
        return service.saveRoom(null, req);
    }

    @PutMapping("/training-rooms/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingRoom updateRoom(@PathVariable UUID id,
                                   @RequestBody TrainingSessionService.RoomRequest req) {
        return service.saveRoom(id, req);
    }

    // ── Sessions ────────────────────────────────────────────────────────────

    @GetMapping("/training-sessions")
    @PreAuthorize("isAuthenticated()")
    public List<TrainingSession> sessions(@RequestParam(required = false) UUID courseId,
                                          @RequestParam(required = false) String status) {
        return service.listSessions(courseId, status);
    }

    @PostMapping("/training-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingSession createSession(@RequestBody TrainingSessionService.SessionRequest req) {
        return service.saveSession(null, req);
    }

    @PutMapping("/training-sessions/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingSession updateSession(@PathVariable UUID id,
                                         @RequestBody TrainingSessionService.SessionRequest req) {
        return service.saveSession(id, req);
    }

    @PostMapping("/training-sessions/{id}/status/{status}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingSession changeStatus(@PathVariable UUID id, @PathVariable String status) {
        return service.changeSessionStatus(id, status);
    }

    // ── Attendance ──────────────────────────────────────────────────────────

    @GetMapping("/training-sessions/{id}/attendance")
    @PreAuthorize("isAuthenticated()")
    public List<TrainingAttendance> attendance(@PathVariable UUID id) {
        return service.attendanceFor(id);
    }

    @PostMapping("/training-sessions/{id}/attendance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingAttendance enrol(@PathVariable UUID id, @RequestParam UUID employeeId) {
        return service.enrol(id, employeeId);
    }

    @PostMapping("/training-attendance/{attendanceId}/mark/{status}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingAttendance mark(@PathVariable UUID attendanceId, @PathVariable String status,
                                   @RequestParam(required = false) String note) {
        return service.mark(attendanceId, status, note);
    }
}
