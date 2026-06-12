import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
  Timeline,
  DatePicker,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  recruitmentApi,
  type Application,
  type ApplicationEvent,
  type ApplicationStage,
  type Candidate,
  type Offer,
  type Vacancy,
  type VacancyStatus,
} from '../api/recruitment'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'
import { JobPostingsPanel } from '../components/JobPostingsPanel'

const STAGE_ORDER: ApplicationStage[] = [
  'CV_SCREENING',
  'HR_INTERVIEW',
  'TECHNICAL_INTERVIEW',
  'FINAL_INTERVIEW',
  'OFFER',
  'HIRED',
]

const STAGE_COLOR: Record<ApplicationStage, string> = {
  CV_SCREENING: 'blue',
  HR_INTERVIEW: 'geekblue',
  TECHNICAL_INTERVIEW: 'purple',
  FINAL_INTERVIEW: 'magenta',
  OFFER: 'gold',
  HIRED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'default',
}

const STATUS_COLOR: Record<VacancyStatus, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'cyan',
  REJECTED: 'red',
  OPEN: 'green',
  PUBLISHED: 'green',
  PAUSED: 'orange',
  ON_HOLD: 'orange',
  CLOSED: 'default',
  FILLED: 'blue',
  CANCELLED: 'red',
}

export function VacancyDetailPage() {
  const { id = '' } = useParams()
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole(...RoleSets.RECRUITMENT_TEAM)

  const [vacancy, setVacancy] = useState<Vacancy | null>(null)
  const [applications, setApplications] = useState<Application[]>([])
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [loading, setLoading] = useState(true)

  const [addOpen, setAddOpen] = useState(false)
  const [pickCandidate, setPickCandidate] = useState<string | undefined>()

  const [transitionTarget, setTransitionTarget] = useState<{
    application: Application
    toStage: ApplicationStage
  } | null>(null)
  const [tForm] = Form.useForm()

  const [historyOf, setHistoryOf] = useState<Application | null>(null)
  const [history, setHistory] = useState<ApplicationEvent[]>([])

  const [offerOpen, setOfferOpen] = useState<Application | null>(null)
  const [currentOffer, setCurrentOffer] = useState<Offer | null>(null)
  const [oForm] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const [v, apps, cands] = await Promise.all([
        recruitmentApi.vacancy(id),
        recruitmentApi.applicationsByVacancy(id),
        recruitmentApi.candidates({ size: 500 }),
      ])
      setVacancy(v)
      setApplications(apps)
      setCandidates(cands.content)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to load',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const candidateMap = useMemo(
    () => new Map(candidates.map((c) => [c.id, c])),
    [candidates],
  )

  if (loading || !vacancy) {
    return (
      <div style={{ textAlign: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  const addApplicant = async () => {
    if (!pickCandidate) {
      message.warning('Pick a candidate first')
      return
    }
    try {
      await recruitmentApi.apply(vacancy.id, pickCandidate)
      message.success('Application created')
      setAddOpen(false)
      setPickCandidate(undefined)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Could not add applicant',
      )
    }
  }

  const beginTransition = (application: Application, toStage: ApplicationStage) => {
    setTransitionTarget({ application, toStage })
    tForm.resetFields()
  }

  const submitTransition = async () => {
    if (!transitionTarget) return
    const values = await tForm.validateFields()
    try {
      await recruitmentApi.transition(transitionTarget.application.id, {
        toStage: transitionTarget.toStage,
        rating: values.rating,
        recommendation: values.recommendation,
        comment: values.comment,
      })
      message.success(`Moved to ${transitionTarget.toStage}`)
      setTransitionTarget(null)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Transition failed',
      )
    }
  }

  const showHistory = async (a: Application) => {
    setHistoryOf(a)
    setHistory(await recruitmentApi.applicationHistory(a.id))
  }

  const openOffer = async (a: Application) => {
    setOfferOpen(a)
    const existing = await recruitmentApi.offerForApplication(a.id)
    setCurrentOffer(existing)
    oForm.setFieldsValue({
      proposedSalary: existing?.proposedSalary ?? vacancy.salaryMin ?? undefined,
      currency: existing?.currency ?? vacancy.currency,
      proposedStartDate: existing?.proposedStartDate
        ? dayjs(existing.proposedStartDate)
        : dayjs().add(1, 'month').startOf('month'),
      benefits: existing?.benefits ?? undefined,
      notes: existing?.notes ?? undefined,
    })
  }

  const saveOffer = async () => {
    if (!offerOpen) return
    const v = await oForm.validateFields()
    try {
      await recruitmentApi.upsertOffer(offerOpen.id, {
        proposedSalary: v.proposedSalary,
        currency: v.currency,
        proposedStartDate: v.proposedStartDate.format('YYYY-MM-DD'),
        benefits: v.benefits,
        notes: v.notes,
      })
      message.success('Offer saved')
      setOfferOpen(null)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const grouped: Record<ApplicationStage, Application[]> = STAGE_ORDER.reduce(
    (acc, s) => ({ ...acc, [s]: [] }),
    {} as Record<ApplicationStage, Application[]>,
  )
  grouped.REJECTED = []
  grouped.WITHDRAWN = []
  for (const a of applications) {
    if (!grouped[a.currentStage]) grouped[a.currentStage] = []
    grouped[a.currentStage].push(a)
  }

  const nextStageOf = (s: ApplicationStage): ApplicationStage | null => {
    const i = STAGE_ORDER.indexOf(s)
    if (i < 0 || i + 1 >= STAGE_ORDER.length) return null
    return STAGE_ORDER[i + 1]
  }

  const renderCard = (a: Application) => {
    const c = candidateMap.get(a.candidateId)
    const next = nextStageOf(a.currentStage)
    return (
      <Card
        key={a.id}
        size="small"
        style={{ marginBottom: 8 }}
        bodyStyle={{ padding: 10 }}
        title={
          <Space size={4} wrap>
            <Typography.Text strong>
              {c ? `${c.firstName} ${c.lastName}` : a.candidateId.slice(0, 8)}
            </Typography.Text>
            <Tag color="default" style={{ fontSize: 11 }}>{a.applicationNo}</Tag>
          </Space>
        }
        extra={
          <Button size="small" onClick={() => showHistory(a)}>
            History
          </Button>
        }
      >
        {c && (
          <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
            {c.email ?? c.phone ?? '—'}
            {c.expectedSalary
              ? ` · expects ${c.expectedSalary} ${c.currency}`
              : ''}
          </Typography.Text>
        )}
        {a.status === 'IN_PROGRESS' && canEdit && (
          <Space size={4} wrap style={{ marginTop: 8 }}>
            {a.currentStage === 'OFFER' ? (
              <Button size="small" type="primary" onClick={() => openOffer(a)}>
                Offer
              </Button>
            ) : null}
            {next && (
              <Button
                size="small"
                type="primary"
                onClick={() => beginTransition(a, next)}
              >
                {next === 'HIRED' ? 'Hire' : `→ ${next.replace(/_/g, ' ')}`}
              </Button>
            )}
            <Button size="small" danger onClick={() => beginTransition(a, 'REJECTED')}>
              Reject
            </Button>
            <Button size="small" onClick={() => beginTransition(a, 'WITHDRAWN')}>
              Withdraw
            </Button>
          </Space>
        )}
        {a.status === 'HIRED' && a.createdEmployeeId && (
          <Tag color="green" style={{ marginTop: 8 }}>
            Hired →{' '}
            <Link to={`/employees/${a.createdEmployeeId}`}>employee record</Link>
          </Tag>
        )}
      </Card>
    )
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title={
          <Space wrap>
            <Link to="/recruitment/vacancies">Vacancies</Link> /{' '}
            <strong>{vacancy.title}</strong>
            <Tag color="blue">{vacancy.vacancyNo}</Tag>
            <Tag color={STATUS_COLOR[vacancy.status]}>{vacancy.status.replace(/_/g, ' ')}</Tag>
            {vacancy.confidential && <Tag color="purple">CONFIDENTIAL</Tag>}
          </Space>
        }
        extra={
          canEdit && (
            <Space>
              <Button onClick={() => navigate(`/recruitment/vacancies/${vacancy.id}/edit`)}>
                Edit
              </Button>
              {/* M275 — requisition approval workflow */}
              {(vacancy.status === 'DRAFT' || vacancy.status === 'REJECTED') && (
                <Popconfirm
                  title="Submit this requisition for approval?"
                  onConfirm={() =>
                    recruitmentApi
                      .submitVacancyApproval(vacancy.id)
                      .then(load)
                      .catch((err) =>
                        message.error(err?.response?.data?.message ?? 'Submit failed'),
                      )
                  }
                >
                  <Button type="primary">Submit for approval</Button>
                </Popconfirm>
              )}
              {vacancy.status === 'APPROVED' && (
                <Popconfirm
                  title="Open this requisition for applications?"
                  onConfirm={() =>
                    recruitmentApi
                      .changeVacancyStatus(vacancy.id, 'OPEN')
                      .then(load)
                      .catch((err) =>
                        message.error(err?.response?.data?.message ?? 'Open failed'),
                      )
                  }
                >
                  <Button type="primary">Open vacancy</Button>
                </Popconfirm>
              )}
              {vacancy.status === 'OPEN' && (
                <>
                  <Popconfirm
                    title="Put vacancy on hold?"
                    onConfirm={() =>
                      recruitmentApi.changeVacancyStatus(vacancy.id, 'ON_HOLD').then(load)
                    }
                  >
                    <Button>Hold</Button>
                  </Popconfirm>
                  <Popconfirm
                    title="Cancel this vacancy?"
                    onConfirm={() =>
                      recruitmentApi.changeVacancyStatus(vacancy.id, 'CANCELLED').then(load)
                    }
                  >
                    <Button danger>Cancel</Button>
                  </Popconfirm>
                </>
              )}
              <Button type="primary" onClick={() => setAddOpen(true)}>
                Add applicant
              </Button>
            </Space>
          )
        }
      >
        <Descriptions size="small" column={3} bordered>
          <Descriptions.Item label="Department">{vacancy.department ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Location">{vacancy.location ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Openings">{vacancy.openings}</Descriptions.Item>
          <Descriptions.Item label="Salary">
            {vacancy.salaryMin || vacancy.salaryMax
              ? `${vacancy.salaryMin ?? '—'} – ${vacancy.salaryMax ?? '—'} ${vacancy.currency}`
              : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Opening date">
            {vacancy.openingDate ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Closing date">
            {vacancy.closingDate ?? '—'}
          </Descriptions.Item>
        </Descriptions>
        {vacancy.description && (
          <Typography.Paragraph style={{ marginTop: 12 }}>
            <strong>Description:</strong> {vacancy.description}
          </Typography.Paragraph>
        )}
        {vacancy.requirements && (
          <Typography.Paragraph>
            <strong>Requirements:</strong> {vacancy.requirements}
          </Typography.Paragraph>
        )}
      </Card>

      {/* M278 — channel/language-specific job postings (PRD §8) */}
      <JobPostingsPanel vacancyId={vacancy.id} canEdit={canEdit} />

      <Card title="Pipeline">
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(6, minmax(180px, 1fr))',
            gap: 12,
            overflowX: 'auto',
          }}
        >
          {STAGE_ORDER.map((s) => (
            <div key={s}>
              <Tag color={STAGE_COLOR[s]} style={{ marginBottom: 8 }}>
                {s.replace(/_/g, ' ')} · {grouped[s].length}
              </Tag>
              {grouped[s].map(renderCard)}
            </div>
          ))}
        </div>
        {(grouped.REJECTED.length > 0 || grouped.WITHDRAWN.length > 0) && (
          <div style={{ marginTop: 24 }}>
            <Typography.Title level={5}>Closed</Typography.Title>
            <Space direction="vertical" style={{ width: '100%' }}>
              {[...grouped.REJECTED, ...grouped.WITHDRAWN].map(renderCard)}
            </Space>
          </div>
        )}
      </Card>

      <Modal
        open={addOpen}
        title="Add applicant"
        onCancel={() => setAddOpen(false)}
        onOk={addApplicant}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="Pick a candidate"
            style={{ width: '100%' }}
            options={candidates.map((c) => ({
              value: c.id,
              label: `${c.candidateNo} — ${c.firstName} ${c.lastName}${
                c.email ? ` · ${c.email}` : ''
              }`,
            }))}
            value={pickCandidate}
            onChange={setPickCandidate}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Need a new candidate? Create one from the{' '}
            <Link to="/recruitment/candidates/new">Candidates page</Link>.
          </Typography.Text>
        </Space>
      </Modal>

      <Modal
        open={!!transitionTarget}
        title={
          transitionTarget
            ? `Transition to ${transitionTarget.toStage.replace(/_/g, ' ')}`
            : ''
        }
        onCancel={() => setTransitionTarget(null)}
        onOk={submitTransition}
        okText={transitionTarget?.toStage === 'HIRED' ? 'Hire' : 'Save'}
      >
        <Form form={tForm} layout="vertical">
          <Form.Item name="rating" label="Rating (1–5)">
            <InputNumber min={1} max={5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="recommendation" label="Recommendation">
            <Select
              allowClear
              options={[
                { value: 'STRONG_HIRE', label: 'Strong hire' },
                { value: 'HIRE', label: 'Hire' },
                { value: 'NO_HIRE', label: 'No hire' },
                { value: 'STRONG_NO_HIRE', label: 'Strong no hire' },
              ]}
            />
          </Form.Item>
          <Form.Item name="comment" label="Comment">
            <Input.TextArea rows={3} />
          </Form.Item>
          {transitionTarget?.toStage === 'HIRED' && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Hiring creates an Employee record on probation and increments the linked
              position's occupied headcount.
            </Typography.Text>
          )}
        </Form>
      </Modal>

      <Modal
        open={!!historyOf}
        title={
          historyOf
            ? `Pipeline history — ${historyOf.applicationNo}`
            : ''
        }
        footer={null}
        width={640}
        onCancel={() => setHistoryOf(null)}
      >
        <Timeline
          items={history.map((e) => ({
            color: STAGE_COLOR[(e.toStage ?? e.fromStage ?? 'CV_SCREENING') as ApplicationStage] ?? 'gray',
            children: (
              <Space direction="vertical" size={2}>
                <Space>
                  <Tag>{e.eventType}</Tag>
                  {e.fromStage && <Tag color="default">{e.fromStage}</Tag>}
                  {e.toStage && (
                    <Tag color={STAGE_COLOR[e.toStage]}>→ {e.toStage}</Tag>
                  )}
                  {e.rating && <Tag color="gold">★ {e.rating}</Tag>}
                  {e.recommendation && <Tag>{e.recommendation}</Tag>}
                </Space>
                <Typography.Text style={{ fontSize: 12 }} type="secondary">
                  by <b>{e.actor}</b> · {dayjs(e.createdAt).format('YYYY-MM-DD HH:mm')}
                </Typography.Text>
                {e.comment && (
                  <Typography.Text italic style={{ display: 'block' }}>
                    “{e.comment}”
                  </Typography.Text>
                )}
              </Space>
            ),
          }))}
        />
      </Modal>

      <Modal
        open={!!offerOpen}
        title={
          offerOpen
            ? `Offer — ${candidateMap.get(offerOpen.candidateId)?.firstName ?? ''} ${
                candidateMap.get(offerOpen.candidateId)?.lastName ?? ''
              }`
            : ''
        }
        onCancel={() => setOfferOpen(null)}
        onOk={saveOffer}
        okText={currentOffer ? 'Save offer' : 'Create offer'}
        width={560}
      >
        {currentOffer && (
          <Space style={{ marginBottom: 12 }}>
            <Tag>{currentOffer.offerNo}</Tag>
            <Tag color="geekblue">{currentOffer.status.replace(/_/g, ' ')}</Tag>
            {currentOffer.salaryException && (
              <Tag color="orange">SALARY EXCEPTION</Tag>
            )}
          </Space>
        )}
        <Form form={oForm} layout="vertical">
          <Form.Item
            name="proposedSalary"
            label="Proposed monthly salary"
            rules={[{ required: true }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="currency"
            label="Currency"
            rules={[{ required: true, min: 3, max: 3 }]}
          >
            <Input maxLength={3} />
          </Form.Item>
          <Form.Item
            name="proposedStartDate"
            label="Proposed start date"
            rules={[{ required: true }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="benefits" label="Benefits">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
        {/* M276 — offers travel DRAFT → PENDING_APPROVAL → APPROVED → SENT */}
        {currentOffer && currentOffer.status === 'DRAFT' && (
          <Space style={{ marginTop: 8 }}>
            <Button
              type="primary"
              onClick={async () => {
                try {
                  const o = await recruitmentApi.submitOfferApproval(currentOffer.id)
                  message.success(
                    o.salaryException
                      ? 'Submitted — salary outside range, exception approval chain'
                      : 'Submitted for approval',
                  )
                } catch (err) {
                  message.error(
                    (err as { response?: { data?: { message?: string } } }).response?.data
                      ?.message ?? 'Submit failed',
                  )
                }
                if (offerOpen) openOffer(offerOpen)
              }}
            >
              Submit for approval
            </Button>
          </Space>
        )}
        {currentOffer && currentOffer.status === 'PENDING_APPROVAL' && (
          <Tag color="gold" style={{ marginTop: 8 }}>
            Awaiting approval — see the Approvals inbox
          </Tag>
        )}
        {/* M283 — offer letter PDF, available once approved */}
        {currentOffer &&
          ['APPROVED', 'SENT', 'ACCEPTED'].includes(currentOffer.status) && (
            <Space style={{ marginTop: 8 }}>
              {(['az', 'en'] as const).map((lang) => (
                <Button
                  key={lang}
                  size="small"
                  onClick={async () => {
                    try {
                      const blob = await recruitmentApi.downloadOfferLetter(
                        currentOffer.id,
                        lang,
                      )
                      const url = URL.createObjectURL(blob)
                      const a = document.createElement('a')
                      a.href = url
                      a.download = `${currentOffer.offerNo}-${lang}.pdf`
                      a.click()
                      URL.revokeObjectURL(url)
                    } catch {
                      message.error('Letter download failed')
                    }
                  }}
                >
                  Letter ({lang.toUpperCase()})
                </Button>
              ))}
            </Space>
          )}
        {currentOffer && currentOffer.status === 'APPROVED' && (
          <Space style={{ marginTop: 8 }}>
            <Button
              type="primary"
              onClick={async () => {
                try {
                  await recruitmentApi.transitionOffer(currentOffer.id, 'SENT')
                  message.success('Offer marked SENT')
                } catch (err) {
                  message.error(
                    (err as { response?: { data?: { message?: string } } }).response?.data
                      ?.message ?? 'Send failed',
                  )
                }
                if (offerOpen) openOffer(offerOpen)
              }}
            >
              Mark as sent
            </Button>
          </Space>
        )}
        {currentOffer && currentOffer.status === 'SENT' && (
          <Space style={{ marginTop: 8 }}>
            <Button
              type="primary"
              onClick={async () => {
                await recruitmentApi.transitionOffer(currentOffer.id, 'ACCEPTED')
                message.success('Offer ACCEPTED')
                if (offerOpen) openOffer(offerOpen)
              }}
            >
              Mark accepted
            </Button>
            <Button
              danger
              onClick={async () => {
                await recruitmentApi.transitionOffer(currentOffer.id, 'REJECTED')
                message.success('Offer REJECTED')
                if (offerOpen) openOffer(offerOpen)
              }}
            >
              Mark rejected
            </Button>
          </Space>
        )}
      </Modal>
    </Space>
  )
}
