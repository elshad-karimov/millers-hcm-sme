// M86 — Interviewer scoring UI. Lives at /recruitment/interviews/:id and
// is the page interviewers spend their time on:
//   • header with interview metadata + live overall-score preview
//   • per-question scoring (1-5 buttons + comment)
//   • finalize panel with recommendation + overall comment
//   • cancel / no-show shortcuts for terminal states

import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import {
  interviewsApi,
  type Interview,
  type InterviewDetail,
  type InterviewQuestion,
  type InterviewRecommendation,
  type InterviewScore,
  type InterviewStatus,
} from '../api/interviews'

const STATUS_COLOR: Record<InterviewStatus, string> = {
  SCHEDULED: 'blue',
  IN_PROGRESS: 'gold',
  COMPLETED: 'green',
  CANCELLED: 'default',
  NO_SHOW: 'red',
}

const REC_COLOR: Record<InterviewRecommendation, string> = {
  STRONG_HIRE: 'green',
  HIRE: 'cyan',
  MAYBE: 'gold',
  NO_HIRE: 'orange',
  STRONG_NO_HIRE: 'red',
}

const SCORE_BUTTONS = [1, 2, 3, 4, 5] as const
const RECOMMENDATIONS: InterviewRecommendation[] = [
  'STRONG_HIRE', 'HIRE', 'MAYBE', 'NO_HIRE', 'STRONG_NO_HIRE',
]

export function InterviewDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()

  const [detail, setDetail] = useState<InterviewDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [savingScoreFor, setSavingScoreFor] = useState<string | null>(null)
  const [comments, setComments] = useState<Record<string, string>>({})
  const [recommendation, setRecommendation] = useState<InterviewRecommendation | undefined>()
  const [overallComment, setOverallComment] = useState('')
  const [finalizing, setFinalizing] = useState(false)

  const load = () => {
    setLoading(true)
    interviewsApi
      .detail(id)
      .then((d) => {
        setDetail(d)
        // Seed local comment buffers from any existing scores so editing
        // doesn't blank them out.
        const seeded: Record<string, string> = {}
        for (const s of d.scores) seeded[s.questionId] = s.comment ?? ''
        setComments(seeded)
        setRecommendation(d.interview.recommendation ?? undefined)
        setOverallComment(d.interview.overallComment ?? '')
      })
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load interview'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(load, [id])

  // ── Live overall-score preview ──────────────────────────────────────────
  // Mirrors the backend's weighted-average formula so the UI matches
  // exactly what InterviewService.finalize_() will persist.
  const overallPreview = useMemo(() => {
    if (!detail) return null
    let weighted = 0
    let totalWeight = 0
    const scoreByQ = new Map<string, InterviewScore>()
    for (const s of detail.scores) scoreByQ.set(s.questionId, s)
    for (const q of detail.questions) {
      const s = scoreByQ.get(q.id)
      if (!s) continue
      weighted += s.score * q.weight
      totalWeight += q.weight
    }
    if (totalWeight === 0) return null
    return Math.round((weighted / totalWeight) * 100) / 100
  }, [detail])

  const editable = useMemo(() => {
    if (!detail) return false
    const s = detail.interview.status
    return s === 'SCHEDULED' || s === 'IN_PROGRESS'
  }, [detail])

  const requiredUnscored = useMemo(() => {
    if (!detail) return [] as InterviewQuestion[]
    const scored = new Set(detail.scores.map((s) => s.questionId))
    return detail.questions.filter((q) => q.required && q.active && !scored.has(q.id))
  }, [detail])

  // ── Mutations ───────────────────────────────────────────────────────────

  const setScore = async (q: InterviewQuestion, score: number) => {
    if (!editable) return
    setSavingScoreFor(q.id)
    try {
      await interviewsApi.upsertScore(id, {
        questionId: q.id,
        score,
        comment: comments[q.id] || undefined,
      })
      message.success('Score saved')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to save score',
      )
    } finally {
      setSavingScoreFor(null)
    }
  }

  const saveComment = async (q: InterviewQuestion) => {
    if (!editable) return
    const existing = detail?.scores.find((s) => s.questionId === q.id)
    if (!existing) {
      message.info('Pick a score first')
      return
    }
    setSavingScoreFor(q.id)
    try {
      await interviewsApi.upsertScore(id, {
        questionId: q.id,
        score: existing.score,
        comment: comments[q.id] || undefined,
      })
      message.success('Comment saved')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to save comment',
      )
    } finally {
      setSavingScoreFor(null)
    }
  }

  const finalize = async () => {
    if (!recommendation) {
      message.error('Pick a recommendation')
      return
    }
    if (requiredUnscored.length > 0) {
      message.error(
        `Required question${requiredUnscored.length > 1 ? 's' : ''} still unscored: `
          + requiredUnscored.map((q) => q.questionText.slice(0, 40)).join('; '),
      )
      return
    }
    setFinalizing(true)
    try {
      await interviewsApi.finalize(id, {
        recommendation,
        overallComment: overallComment || undefined,
      })
      message.success('Interview finalized')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Finalize failed',
      )
    } finally {
      setFinalizing(false)
    }
  }

  const cancel = async () => {
    try {
      await interviewsApi.cancel(id)
      message.success('Interview cancelled')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Cancel failed',
      )
    }
  }

  const noShow = async () => {
    try {
      await interviewsApi.noShow(id)
      message.success('Marked as no-show')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'No-show failed',
      )
    }
  }

  if (loading || !detail) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  const iv: Interview = detail.interview
  const scoreByQ = new Map(detail.scores.map((s) => [s.questionId, s]))

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={
          <Space>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {iv.interviewNo}
            </Typography.Title>
            <Tag color={STATUS_COLOR[iv.status]}>{iv.status.replace(/_/g, ' ')}</Tag>
            {iv.recommendation && (
              <Tag color={REC_COLOR[iv.recommendation]}>
                {iv.recommendation.replace(/_/g, ' ')}
              </Tag>
            )}
          </Space>
        }
        extra={
          editable && (
            <Space>
              <Popconfirm title="Cancel this interview?" onConfirm={cancel}>
                <Button>Cancel interview</Button>
              </Popconfirm>
              <Popconfirm title="Mark as no-show?" onConfirm={noShow}>
                <Button danger>No-show</Button>
              </Popconfirm>
            </Space>
          )
        }
      >
        <Row gutter={[16, 16]}>
          <Col xs={12} md={6}>
            <Statistic title="Kit" value={detail.kit.code} />
            <Typography.Text type="secondary">{detail.kit.name}</Typography.Text>
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="Scheduled" value={new Date(iv.scheduledAt).toLocaleString()} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic
              title="Overall score (live)"
              value={overallPreview ?? '—'}
              suffix={overallPreview != null ? '/ 5' : undefined}
              valueStyle={
                overallPreview != null && overallPreview >= 4
                  ? { color: '#52c41a' }
                  : overallPreview != null && overallPreview < 3
                  ? { color: '#f5222d' }
                  : {}
              }
            />
          </Col>
          <Col xs={12} md={6}>
            <Statistic
              title="Required unscored"
              value={requiredUnscored.length}
              valueStyle={requiredUnscored.length > 0 ? { color: '#fa8c16' } : {}}
            />
          </Col>
        </Row>
      </Card>

      <Card title={`Questions (${detail.questions.length})`}>
        {detail.questions.length === 0 ? (
          <Empty description="This kit has no questions" />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {detail.questions
              .filter((q) => q.active)
              .map((q) => {
                const current = scoreByQ.get(q.id)
                return (
                  <Card key={q.id} size="small" type="inner"
                    title={
                      <Space>
                        <Typography.Text strong>Q{q.sortOrder + 1}.</Typography.Text>
                        <Tag>weight {q.weight}</Tag>
                        {q.required && <Tag color="red">REQUIRED</Tag>}
                      </Space>
                    }
                    extra={
                      current != null && (
                        <Tag color="blue">scored {current.score}/5</Tag>
                      )
                    }
                  >
                    <Typography.Paragraph style={{ marginBottom: 12 }}>
                      {q.questionText}
                    </Typography.Paragraph>
                    <Space wrap style={{ marginBottom: 12 }}>
                      {SCORE_BUTTONS.map((s) => (
                        <Button
                          key={s}
                          type={current?.score === s ? 'primary' : 'default'}
                          disabled={!editable}
                          loading={savingScoreFor === q.id && current?.score !== s}
                          onClick={() => setScore(q, s)}
                        >
                          {s}
                        </Button>
                      ))}
                    </Space>
                    <Input.TextArea
                      rows={2}
                      placeholder="Comment (optional)"
                      value={comments[q.id] ?? ''}
                      disabled={!editable}
                      onChange={(e) =>
                        setComments({ ...comments, [q.id]: e.target.value })
                      }
                      onBlur={() => {
                        // Persist if the comment changed and a score exists.
                        if (
                          current
                          && (current.comment ?? '') !== (comments[q.id] ?? '')
                        ) {
                          saveComment(q)
                        }
                      }}
                    />
                  </Card>
                )
              })}
          </Space>
        )}
      </Card>

      {iv.status === 'COMPLETED' ? (
        <Alert
          type="success"
          showIcon
          message={`Finalized — overall ${iv.overallScore ?? '—'}/5`}
          description={iv.overallComment ?? null}
        />
      ) : iv.status === 'CANCELLED' || iv.status === 'NO_SHOW' ? (
        <Alert
          type="warning"
          showIcon
          message={`Interview ${iv.status.replace(/_/g, ' ').toLowerCase()}`}
          description={iv.overallComment ?? null}
        />
      ) : (
        <Card title="Finalize">
          <Space direction="vertical" style={{ width: '100%' }}>
            <Select
              placeholder="Pick a recommendation"
              style={{ width: 320 }}
              value={recommendation}
              onChange={setRecommendation}
              options={RECOMMENDATIONS.map((r) => ({ value: r, label: r.replace(/_/g, ' ') }))}
            />
            <Input.TextArea
              rows={3}
              placeholder="Overall comment"
              value={overallComment}
              onChange={(e) => setOverallComment(e.target.value)}
            />
            <Space>
              <Button type="primary" onClick={finalize} loading={finalizing}
                disabled={!recommendation || requiredUnscored.length > 0}>
                Finalize
              </Button>
              <Button onClick={() => navigate(-1)}>Back</Button>
            </Space>
            {requiredUnscored.length > 0 && (
              <Typography.Text type="warning">
                {requiredUnscored.length} required question{requiredUnscored.length > 1 ? 's' : ''}
                {' '}must be scored before finalising.
              </Typography.Text>
            )}
          </Space>
        </Card>
      )}
    </Space>
  )
}
