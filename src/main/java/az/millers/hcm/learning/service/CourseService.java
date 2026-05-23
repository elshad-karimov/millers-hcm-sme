package az.millers.hcm.learning.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.learning.api.dto.CourseCompetencyRequest;
import az.millers.hcm.learning.api.dto.CourseRequest;
import az.millers.hcm.learning.api.dto.CourseResponse;
import az.millers.hcm.learning.api.dto.QuestionRequest;
import az.millers.hcm.learning.domain.Course;
import az.millers.hcm.learning.domain.CourseCategory;
import az.millers.hcm.learning.domain.CourseCompetency;
import az.millers.hcm.learning.domain.CourseStatus;
import az.millers.hcm.learning.domain.QuestionType;
import az.millers.hcm.learning.domain.QuizQuestion;
import az.millers.hcm.learning.repo.CompetencyRepository;
import az.millers.hcm.learning.repo.CourseCompetencyRepository;
import az.millers.hcm.learning.repo.CourseRepository;
import az.millers.hcm.learning.repo.QuizQuestionRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class CourseService {

    private static final String MODULE = "LEARNING";
    private static final String ENTITY = "Course";

    private final CourseRepository courses;
    private final QuizQuestionRepository questions;
    private final CompetencyRepository competencies;
    private final CourseCompetencyRepository courseCompetencies;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CourseService(CourseRepository courses,
                         QuizQuestionRepository questions,
                         CompetencyRepository competencies,
                         CourseCompetencyRepository courseCompetencies,
                         AuditService audit,
                         CurrentRequest currentRequest) {
        this.courses = courses;
        this.questions = questions;
        this.competencies = competencies;
        this.courseCompetencies = courseCompetencies;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public Course get(UUID id) {
        return courses.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Course> list(CourseStatus status, CourseCategory category, Pageable pageable) {
        if (status != null)   return courses.findByStatusOrderByTitleAsc(status, pageable);
        if (category != null) return courses.findByCategoryOrderByTitleAsc(category, pageable);
        return courses.findAllByOrderByTitleAsc(pageable);
    }

    @Transactional(readOnly = true)
    public List<QuizQuestion> questionsFor(UUID courseId) {
        return questions.findByCourseIdOrderByQuestionNoAsc(courseId);
    }

    @Transactional(readOnly = true)
    public List<CourseCompetency> competencyMappingFor(UUID courseId) {
        return courseCompetencies.findByCourseId(courseId);
    }

    @Transactional
    public Course create(CourseRequest req) {
        if (courses.existsByCode(req.code())) {
            throw new BadRequestException("Course code already exists: " + req.code());
        }
        Course c = new Course();
        c.setCourseNo(String.format("CRS-%05d", courses.nextNoSequence()));
        apply(c, req);
        c.setStatus(CourseStatus.DRAFT);
        c.setCreatedBy(currentRequest.username());
        c.setUpdatedBy(currentRequest.username());
        Course saved = courses.save(c);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, CourseResponse.from(saved));
        return saved;
    }

    @Transactional
    public Course update(UUID id, CourseRequest req) {
        Course c = get(id);
        if (c.getStatus() == CourseStatus.ARCHIVED) {
            throw new BadRequestException("Cannot edit an archived course");
        }
        CourseResponse before = CourseResponse.from(c);
        apply(c, req);
        c.setUpdatedBy(currentRequest.username());
        Course saved = courses.save(c);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, CourseResponse.from(saved));
        return saved;
    }

    @Transactional
    public Course publish(UUID id) {
        Course c = get(id);
        if (c.getStatus() == CourseStatus.PUBLISHED) {
            throw new BadRequestException("Course is already published");
        }
        if (questions.countByCourseId(c.getId()) == 0) {
            throw new BadRequestException(
                    "Add at least one quiz question before publishing the course");
        }
        c.setStatus(CourseStatus.PUBLISHED);
        c.setPublishedAt(OffsetDateTime.now());
        c.setUpdatedBy(currentRequest.username());
        Course saved = courses.save(c);
        audit.record(MODULE, ENTITY, id.toString(),
                "PUBLISH", null, CourseResponse.from(saved));
        return saved;
    }

    @Transactional
    public Course archive(UUID id) {
        Course c = get(id);
        if (c.getStatus() == CourseStatus.ARCHIVED) {
            throw new BadRequestException("Course is already archived");
        }
        c.setStatus(CourseStatus.ARCHIVED);
        c.setArchivedAt(OffsetDateTime.now());
        c.setUpdatedBy(currentRequest.username());
        Course saved = courses.save(c);
        audit.record(MODULE, ENTITY, id.toString(),
                "ARCHIVE", null, CourseResponse.from(saved));
        return saved;
    }

    @Transactional
    public QuizQuestion addQuestion(UUID courseId, QuestionRequest req) {
        Course c = get(courseId);
        if (c.getStatus() == CourseStatus.ARCHIVED) {
            throw new BadRequestException("Cannot edit an archived course");
        }
        if (req.correctKeys().isEmpty()) {
            throw new BadRequestException("At least one correctKey must be provided");
        }
        if (req.questionType() == QuestionType.MULTIPLE_CHOICE
                && req.correctKeys().size() != 1) {
            throw new BadRequestException(
                    "MULTIPLE_CHOICE questions require exactly one correct key");
        }
        QuizQuestion q = new QuizQuestion();
        q.setCourseId(courseId);
        q.setQuestionType(req.questionType());
        q.setQuestionText(req.questionText());
        q.setChoices(req.choices());
        q.setCorrectKeys(req.correctKeys());
        q.setExplanation(req.explanation());
        q.setPoints(req.points() == null ? 1 : req.points());
        q.setQuestionNo(req.questionNo() == null
                ? (int) (questions.countByCourseId(courseId) + 1)
                : req.questionNo());
        QuizQuestion saved = questions.save(q);
        audit.record(MODULE, "QuizQuestion", saved.getId().toString(),
                "CREATE", null, Long.toString(saved.getQuestionNo()));
        return saved;
    }

    @Transactional
    public void mapCompetency(UUID courseId, CourseCompetencyRequest req) {
        if (!courses.existsById(courseId)) {
            throw new BadRequestException("Course not found: " + courseId);
        }
        if (!competencies.existsById(req.competencyId())) {
            throw new BadRequestException("Competency not found: " + req.competencyId());
        }
        CourseCompetency cc = new CourseCompetency();
        cc.setCourseId(courseId);
        cc.setCompetencyId(req.competencyId());
        cc.setAwardedLevel(req.awardedLevel() == null ? 3 : req.awardedLevel());
        courseCompetencies.save(cc);
        audit.record(MODULE, "CourseCompetency", courseId + ":" + req.competencyId(),
                "MAP", null, "level=" + cc.getAwardedLevel());
    }

    private void apply(Course c, CourseRequest req) {
        c.setCode(req.code());
        c.setTitle(req.title());
        c.setDescription(req.description());
        c.setContentMarkdown(req.contentMarkdown());
        c.setCategory(req.category());
        c.setDurationHours(req.durationHours() == null ? BigDecimal.ONE : req.durationHours());
        c.setMandatory(req.mandatory());
        c.setPassingScore(req.passingScore() == null ? 70 : clampScore(req.passingScore()));
        c.setMaxAttempts(req.maxAttempts() == null ? 3 : Math.max(1, req.maxAttempts()));
        c.setInstructorId(req.instructorId());
        c.setValidForMonths(req.validForMonths());
        c.setCoverUrl(req.coverUrl());
    }

    private int clampScore(int v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }
}
