import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import {
  learningApi,
  type Attempt,
  type AttemptSubmitAnswer,
  type Choice,
  type Competency,
  type Course,
  type CourseCompetencyMapping,
  type Enrollment,
  type Question,
  type QuestionType,
} from '../api/learning'
import { employeesApi, type Employee } from '../api/employees'
import { AttachmentUploader } from '../components/AttachmentUploader'
import { EmployeePicker } from '../components/EmployeePicker'
import { FormPageShell } from '../components/FormPageShell'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const LIST_PATH = '/learning/courses'

const QTYPE_LABEL: Record<QuestionType, string> = {
  MULTIPLE_CHOICE: 'Single choice',
  MULTI_SELECT: 'Multiple selection',
  TRUE_FALSE: 'True / False',
}

interface NewQuestionForm {
  questionType: QuestionType
  questionText: string
  choices: { key: string; text: string }[]
  correctKeys: string[]
  points: number
  explanation?: string
}

export function CourseDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { message, modal } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canManage = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [course, setCourse] = useState<Course | null>(null)
  const [questions, setQuestions] = useState<Question[]>([])
  const [mappings, setMappings] = useState<CourseCompetencyMapping[]>([])
  const [competencies, setCompetencies] = useState<Competency[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [myEnrollments, setMyEnrollments] = useState<Enrollment[]>([])
  const [loading, setLoading] = useState(true)

  // quiz state
  const [activeAttempt, setActiveAttempt] = useState<Attempt | null>(null)
  const [activeEnrollment, setActiveEnrollment] = useState<Enrollment | null>(null)
  const [quizAnswers, setQuizAnswers] = useState<Record<string, string[]>>({})
  const [submitting, setSubmitting] = useState(false)
  const [lastResult, setLastResult] = useState<Attempt | null>(null)

  // admin modals
  const [enrollOpen, setEnrollOpen] = useState(false)
  const [enrollEmployeeId, setEnrollEmployeeId] = useState<string | undefined>()
  const [questionOpen, setQuestionOpen] = useState(false)
  const [qForm] = Form.useForm<NewQuestionForm>()
  const [mapOpen, setMapOpen] = useState(false)
  const [mapForm] = Form.useForm<{ competencyId: string; awardedLevel: number }>()

  const load = async () => {
    if (!id) return
    setLoading(true)
    try {
      const [c, qs, comps, mps] = await Promise.all([
        learningApi.course(id),
        learningApi.questions(id, canManage),
        learningApi.competencies().catch(() => []),
        learningApi.courseCompetencies(id).catch(() => []),
      ])
      setCourse(c)
      setQuestions(qs)
      setCompetencies(comps)
      setMappings(mps)
      if (canManage) {
        const emps = await employeesApi.list({ size: 500 })
        setEmployees(emps.content)
      }
      const my = await learningApi
        .enrollments({ courseId: id, size: 100 })
        .then((p) => p.content)
        .catch(() => [])
      setMyEnrollments(my)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to load course',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const compMap = useMemo(() => new Map(competencies.map((c) => [c.id, c])), [competencies])
  const empMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])

  if (loading || !course) {
    return (
      <FormPageShell title="Course" backTo={LIST_PATH}>
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      </FormPageShell>
    )
  }

  const onPublish = () =>
    modal.confirm({
      title: 'Publish course?',
      content: 'Employees will be able to enroll. The course must have at least one quiz question.',
      onOk: async () => {
        try {
          await learningApi.publishCourse(course.id)
          message.success('Course published')
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Publish failed',
          )
        }
      },
    })

  const onArchive = () =>
    modal.confirm({
      title: 'Archive course?',
      onOk: async () => {
        try {
          await learningApi.archiveCourse(course.id)
          message.success('Course archived')
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Archive failed',
          )
        }
      },
    })

  const onEnroll = async () => {
    if (!enrollEmployeeId) return
    try {
      await learningApi.enroll({
        courseId: course.id,
        employeeId: enrollEmployeeId,
        enrolledVia: 'ASSIGNED',
      })
      message.success('Employee enrolled')
      setEnrollOpen(false)
      setEnrollEmployeeId(undefined)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Enrollment failed',
      )
    }
  }

  const onAddQuestion = async (v: NewQuestionForm) => {
    if (!v.choices.length) {
      message.error('At least one choice required')
      return
    }
    try {
      await learningApi.addQuestion(course.id, {
        questionType: v.questionType,
        questionText: v.questionText,
        choices: v.choices,
        correctKeys:
          v.questionType === 'MULTIPLE_CHOICE' || v.questionType === 'TRUE_FALSE'
            ? v.correctKeys.slice(0, 1)
            : v.correctKeys,
        points: v.points ?? 1,
        explanation: v.explanation,
      })
      message.success('Question added')
      setQuestionOpen(false)
      qForm.resetFields()
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const onMapCompetency = async (v: { competencyId: string; awardedLevel: number }) => {
    try {
      await learningApi.mapCompetency(course.id, v.competencyId, v.awardedLevel)
      message.success('Competency mapped')
      setMapOpen(false)
      mapForm.resetFields()
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const startQuiz = async (enrollment: Enrollment) => {
    try {
      const a = await learningApi.startAttempt(enrollment.id)
      const qsForAttempt = await learningApi.questions(course.id, false)
      setQuestions(qsForAttempt)
      setQuizAnswers({})
      setActiveAttempt(a)
      setActiveEnrollment(enrollment)
      setLastResult(null)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Could not start quiz',
      )
    }
  }

  const submitQuiz = async () => {
    if (!activeAttempt) return
    setSubmitting(true)
    try {
      const answers: AttemptSubmitAnswer[] = questions.map((q) => ({
        questionId: q.id,
        selectedKeys: quizAnswers[q.id] ?? [],
      }))
      const result = await learningApi.submitAttempt(activeAttempt.id, answers)
      setLastResult(result)
      setActiveAttempt(null)
      setActiveEnrollment(null)
      message.success(result.status === 'PASSED' ? 'Passed! Certificate issued.' : 'Attempt graded.')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Submission failed',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <FormPageShell title={`${course.courseNo} — ${course.title}`} backTo={LIST_PATH}>
      <Space direction="vertical" size="middle" style={{ width: '100%', maxWidth: 1100 }}>
        <Card
          extra={
            <Space wrap>
              <Tag color={course.status === 'PUBLISHED' ? 'green' : 'default'}>
                {course.status}
              </Tag>
              {course.mandatory && <Tag color="red">MANDATORY</Tag>}
              {canManage && course.status === 'DRAFT' && (
                <>
                  <Button onClick={() => navigate(`/learning/courses/${course.id}/edit`)}>
                    Edit
                  </Button>
                  <Button type="primary" onClick={onPublish}>Publish</Button>
                </>
              )}
              {canManage && course.status === 'PUBLISHED' && (
                <>
                  <Button onClick={() => setEnrollOpen(true)}>Enroll employee</Button>
                  <Button danger onClick={onArchive}>Archive</Button>
                </>
              )}
            </Space>
          }
        >
          <Descriptions column={2} size="small">
            <Descriptions.Item label="Code">{course.code}</Descriptions.Item>
            <Descriptions.Item label="Category">{course.category.replace(/_/g, ' ')}</Descriptions.Item>
            <Descriptions.Item label="Duration">{course.durationHours} h</Descriptions.Item>
            <Descriptions.Item label="Passing score">{course.passingScore}%</Descriptions.Item>
            <Descriptions.Item label="Max attempts">{course.maxAttempts}</Descriptions.Item>
            <Descriptions.Item label="Certificate valid for">
              {course.validForMonths ? `${course.validForMonths} months` : 'no expiry'}
            </Descriptions.Item>
          </Descriptions>
          {course.description && (
            <Typography.Paragraph style={{ marginTop: 12 }}>
              {course.description}
            </Typography.Paragraph>
          )}
        </Card>

        {course.contentMarkdown && (
          <Card title="Course content" size="small">
            <pre
              style={{
                whiteSpace: 'pre-wrap',
                fontFamily: 'inherit',
                margin: 0,
                fontSize: 13,
                lineHeight: 1.6,
              }}
            >
              {course.contentMarkdown}
            </pre>
          </Card>
        )}

        <Card title="Course material" size="small">
          <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
            Slides, exercise sheets, supplementary PDFs. Instructors and HR
            can upload; everyone enrolled in the course can download.
          </Typography.Paragraph>
          <AttachmentUploader
            ownerModule="LEARNING"
            ownerEntity="Course"
            ownerId={course.id}
          />
        </Card>

        <Card
          title={`Quiz questions (${questions.length})`}
          size="small"
          extra={
            canManage && (
              <Button onClick={() => setQuestionOpen(true)}>Add question</Button>
            )
          }
        >
          {questions.length === 0 ? (
            <Typography.Text type="secondary">
              No questions yet. {canManage && 'Add at least one before publishing.'}
            </Typography.Text>
          ) : (
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              {questions.map((q) => (
                <Card key={q.id} type="inner" size="small" title={`Q${q.questionNo}. ${q.questionText}`}>
                  <Tag>{QTYPE_LABEL[q.questionType]} · {q.points} pt{q.points === 1 ? '' : 's'}</Tag>
                  <ul style={{ marginTop: 8, marginBottom: 0, paddingLeft: 20 }}>
                    {q.choices.map((c: Choice) => {
                      const isCorrect = q.correctKeys?.includes(c.key)
                      return (
                        <li key={c.key} style={{ color: isCorrect ? '#3f8600' : undefined }}>
                          <strong>{c.key}.</strong> {c.text}
                          {isCorrect && <Tag color="green" style={{ marginLeft: 6 }}>correct</Tag>}
                        </li>
                      )
                    })}
                  </ul>
                  {q.explanation && (
                    <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 6 }}>
                      💡 {q.explanation}
                    </Typography.Text>
                  )}
                </Card>
              ))}
            </Space>
          )}
        </Card>

        <Card
          title="Competencies awarded on completion"
          size="small"
          extra={
            canManage && (
              <Button onClick={() => setMapOpen(true)}>Map competency</Button>
            )
          }
        >
          {mappings.length === 0 ? (
            <Typography.Text type="secondary">No competencies mapped.</Typography.Text>
          ) : (
            <Space wrap>
              {mappings.map((m) => {
                const c = compMap.get(m.competencyId)
                return (
                  <Tag key={m.competencyId} color="cyan">
                    {c?.name ?? m.competencyId} · level {m.awardedLevel}
                  </Tag>
                )
              })}
            </Space>
          )}
        </Card>

        {canManage && myEnrollments.length > 0 && (
          <Card title={`Enrollments (${myEnrollments.length})`} size="small">
            <Space direction="vertical" style={{ width: '100%' }}>
              {myEnrollments.map((e) => {
                const emp = empMap.get(e.employeeId)
                return (
                  <Row key={e.id} gutter={16} align="middle">
                    <Col span={2}>{e.enrollmentNo}</Col>
                    <Col span={8}>{emp ? `${emp.employeeNo} — ${emp.firstName} ${emp.lastName}` : e.employeeId}</Col>
                    <Col span={3}><Tag>{e.status}</Tag></Col>
                    <Col span={3}>attempts {e.attemptsUsed} / {course.maxAttempts}</Col>
                    <Col span={4}>best: {e.bestScorePercent != null ? `${e.bestScorePercent}%` : '—'}</Col>
                    <Col span={4}>
                      {(e.status === 'ENROLLED' || e.status === 'IN_PROGRESS') &&
                        e.attemptsUsed < course.maxAttempts && (
                          <Button size="small" onClick={() => startQuiz(e)}>
                            Take quiz
                          </Button>
                        )}
                    </Col>
                  </Row>
                )
              })}
            </Space>
          </Card>
        )}

        {lastResult && (
          <Alert
            type={lastResult.status === 'PASSED' ? 'success' : 'error'}
            showIcon
            message={
              lastResult.status === 'PASSED'
                ? `Passed! Score ${lastResult.scorePercent}% — certificate issued.`
                : `Attempt ${lastResult.attemptNo} graded — ${lastResult.scorePercent}% (needed ${lastResult.passingScore}%).`
            }
            description={`${lastResult.earnedPoints} / ${lastResult.totalPoints} points`}
          />
        )}

        <Space>
          <Button onClick={() => navigate(LIST_PATH)}>Back to catalog</Button>
        </Space>
      </Space>

      <Modal
        open={!!activeAttempt}
        title={`Quiz — ${course.title} (attempt ${activeAttempt?.attemptNo ?? ''})`}
        onCancel={() => {
          modal.confirm({
            title: 'Abandon attempt?',
            content: 'You can resume this attempt later by clicking "Take quiz" again.',
            onOk: () => {
              setActiveAttempt(null)
              setActiveEnrollment(null)
            },
          })
        }}
        onOk={submitQuiz}
        okText="Submit"
        confirmLoading={submitting}
        width={760}
        maskClosable={false}
      >
        <Typography.Paragraph type="secondary">
          You need {course.passingScore}% to pass.{' '}
          {activeEnrollment && `Attempts used: ${activeEnrollment.attemptsUsed} / ${course.maxAttempts}`}
        </Typography.Paragraph>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {questions.map((q) => (
            <Card key={q.id} type="inner" size="small" title={`Q${q.questionNo}. ${q.questionText}`}>
              {q.questionType === 'MULTI_SELECT' ? (
                <Checkbox.Group
                  value={quizAnswers[q.id] ?? []}
                  onChange={(vals) => setQuizAnswers({ ...quizAnswers, [q.id]: vals as string[] })}
                  style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
                  options={q.choices.map((c) => ({ value: c.key, label: `${c.key}. ${c.text}` }))}
                />
              ) : (
                <Radio.Group
                  value={(quizAnswers[q.id] ?? [])[0]}
                  onChange={(e) => setQuizAnswers({ ...quizAnswers, [q.id]: [e.target.value] })}
                  style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
                >
                  {q.choices.map((c) => (
                    <Radio key={c.key} value={c.key}>
                      {c.key}. {c.text}
                    </Radio>
                  ))}
                </Radio.Group>
              )}
            </Card>
          ))}
        </Space>
      </Modal>

      <Modal
        open={enrollOpen}
        title="Enroll employee in this course"
        onCancel={() => setEnrollOpen(false)}
        onOk={onEnroll}
        okText="Enroll"
      >
        <EmployeePicker
          placeholder="Select employee"
          style={{ width: '100%' }}
          value={enrollEmployeeId}
          onChange={setEnrollEmployeeId}
        />
      </Modal>

      <Modal
        open={questionOpen}
        title="Add quiz question"
        onCancel={() => setQuestionOpen(false)}
        onOk={() => qForm.submit()}
        okText="Add"
        width={680}
      >
        <Form
          form={qForm}
          layout="vertical"
          onFinish={onAddQuestion}
          initialValues={{
            questionType: 'MULTIPLE_CHOICE',
            points: 1,
            choices: [
              { key: 'A', text: '' },
              { key: 'B', text: '' },
              { key: 'C', text: '' },
              { key: 'D', text: '' },
            ],
            correctKeys: [],
          }}
        >
          <Form.Item name="questionType" label="Question type" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'MULTIPLE_CHOICE', label: 'Single choice (one correct)' },
                { value: 'MULTI_SELECT', label: 'Multiple selection (more than one correct)' },
                { value: 'TRUE_FALSE', label: 'True / False' },
              ]}
              onChange={(v) => {
                if (v === 'TRUE_FALSE') {
                  qForm.setFieldValue('choices', [
                    { key: 'T', text: 'True' },
                    { key: 'F', text: 'False' },
                  ])
                  qForm.setFieldValue('correctKeys', [])
                }
              }}
            />
          </Form.Item>
          <Form.Item name="questionText" label="Question" rules={[{ required: true }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.List name="choices">
            {(fields, { add, remove }) => (
              <>
                <Typography.Text strong>Choices</Typography.Text>
                {fields.map((field) => (
                  <Row key={field.key} gutter={8} style={{ marginTop: 8 }}>
                    <Col span={3}>
                      <Form.Item {...field} name={[field.name, 'key']} noStyle>
                        <Input placeholder="Key" />
                      </Form.Item>
                    </Col>
                    <Col span={18}>
                      <Form.Item {...field} name={[field.name, 'text']} noStyle>
                        <Input placeholder="Text" />
                      </Form.Item>
                    </Col>
                    <Col span={3}>
                      <Button size="small" onClick={() => remove(field.name)}>
                        Remove
                      </Button>
                    </Col>
                  </Row>
                ))}
                <Button size="small" style={{ marginTop: 8 }} onClick={() => add({ key: '', text: '' })}>
                  Add choice
                </Button>
              </>
            )}
          </Form.List>
          <Form.Item
            name="correctKeys"
            label="Correct choice key(s)"
            tooltip="Type the key letters (e.g. B or A,C). Single-choice/true-false: first key wins."
            rules={[{ required: true }]}
            getValueFromEvent={(e) => {
              const v = (e?.target?.value ?? e) as string
              return typeof v === 'string'
                ? v
                    .split(/[,\s]+/)
                    .map((s) => s.trim().toUpperCase())
                    .filter(Boolean)
                : v
            }}
            getValueProps={(value) => ({
              value: Array.isArray(value) ? value.join(',') : value,
            })}
          >
            <Input placeholder="e.g. B  or  A,C" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="points" label="Points">
                <InputNumber min={1} max={10} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={18}>
              <Form.Item name="explanation" label="Explanation (shown after grading)">
                <Input />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      <Modal
        open={mapOpen}
        title="Map competency awarded on completion"
        onCancel={() => setMapOpen(false)}
        onOk={() => mapForm.submit()}
        okText="Map"
      >
        <Form form={mapForm} layout="vertical" onFinish={onMapCompetency} initialValues={{ awardedLevel: 3 }}>
          <Form.Item name="competencyId" label="Competency" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={competencies.map((c) => ({
                value: c.id,
                label: `${c.code} — ${c.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="awardedLevel" label="Awarded proficiency level (1–5)" rules={[{ required: true }]}>
            <InputNumber min={1} max={5} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </FormPageShell>
  )
}
