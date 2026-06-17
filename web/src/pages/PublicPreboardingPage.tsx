// M122 — Public candidate-facing pre-boarding form.
//
// Rendered at /preboarding/:token OUTSIDE the SPA's RequireAuth wrapper.
// The candidate has no Keycloak account; the magic-link token IS the
// credential. Loads form schema + employee identity from the server,
// renders a multi-step form, submits. Unknown / expired / revoked
// tokens get a friendly 404.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Alert,
  Button,
  Card,
  Checkbox,
  DatePicker,
  Divider,
  Form,
  Input,
  Modal,
  Result,
  Select,
  Space,
  Spin,
  Steps,
  Tag,
  Typography,
} from 'antd'
import { CheckCircleFilled, UserOutlined, TeamOutlined } from '@ant-design/icons'
import { useParams } from 'react-router-dom'
import dayjs from 'dayjs'
import {
  publicPreboardingApi,
  type FormSection,
  type PolicyTask,
  type PublicInfo,
} from '../api/preboarding'

const { Title, Text, Paragraph } = Typography

export function PublicPreboardingPage() {
  const { token } = useParams<{ token: string }>()
  const { message } = AntdApp.useApp()
  const [info, setInfo] = useState<PublicInfo | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [step, setStep] = useState(0)
  const [done, setDone] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [data, setData] = useState<Record<string, unknown>>({})
  // M307 — track in-session acknowledgements
  const [acknowledgedIds, setAcknowledgedIds] = useState<string[]>([])
  const [ackingId, setAckingId] = useState<string | null>(null)
  const [ackModal, setAckModal] = useState<PolicyTask | null>(null)

  useEffect(() => {
    if (!token) return
    setLoading(true)
    publicPreboardingApi.info(token)
      .then(setInfo)
      .catch((e) => {
        if (e?.response?.status === 404) setError('This link is no longer valid.')
        else setError(e?.response?.data?.message ?? 'Failed to load form')
      })
      .finally(() => setLoading(false))
  }, [token])

  if (loading) return <div style={{ padding: 64, textAlign: 'center' }}><Spin /></div>

  if (error || !info) {
    return (
      <Result
        status="404"
        title="Link not available"
        subTitle={error ?? 'This pre-boarding link may have expired or been revoked. Contact your HR contact for a new one.'}
      />
    )
  }

  if (done || info.status === 'SUBMITTED' || info.status === 'COMPLETED') {
    return (
      <Result
        status="success"
        title="Submission received"
        subTitle="Thank you. Your HR team will review and finalise your records before your start date."
      />
    )
  }

  const sections = info.schema.sections
  const currentSection: FormSection = sections[step]

  const pendingPolicies = info.pendingPolicies ?? []
  const unacknowledged = pendingPolicies.filter((p) => !acknowledgedIds.includes(p.taskStatusId))
  const justAcknowledged = pendingPolicies.filter((p) => acknowledgedIds.includes(p.taskStatusId))

  const doAcknowledge = async (policy: PolicyTask) => {
    if (!token) return
    setAckingId(policy.taskStatusId)
    try {
      await publicPreboardingApi.acknowledge(token, policy.taskStatusId)
      setAcknowledgedIds((prev) => [...prev, policy.taskStatusId])
      setAckModal(null)
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Acknowledgement failed')
    } finally {
      setAckingId(null)
    }
  }

  return (
    <div style={{ maxWidth: 720, margin: '24px auto', padding: 16 }}>
      <Card>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>

          {/* ── Welcome hero ── */}
          <div>
            <Title level={3} style={{ margin: 0 }}>Welcome, {info.employeeName}</Title>
            {(info.joinDate || info.role) && (
              <Space size="large" style={{ marginTop: 4 }}>
                {info.joinDate && (
                  <Text type="secondary">
                    Start date: <Text strong>{dayjs(info.joinDate).format('MMMM D, YYYY')}</Text>
                  </Text>
                )}
                {info.role && (
                  <Text type="secondary">
                    Role: <Text strong>{info.role}</Text>
                  </Text>
                )}
              </Space>
            )}
            {info.welcomeMessage && (
              <Paragraph style={{ marginTop: 8, marginBottom: 0, fontStyle: 'italic', color: '#555' }}>
                "{info.welcomeMessage}"
              </Paragraph>
            )}
          </div>

          {/* ── Manager / buddy intro cards ── */}
          {(info.managerName || info.buddyName) && (
            <Space size="middle" wrap>
              {info.managerName && (
                <Card size="small" style={{ minWidth: 200 }}>
                  <Space>
                    <UserOutlined style={{ fontSize: 20, color: '#1677ff' }} />
                    <div>
                      <div style={{ fontSize: 11, color: '#888' }}>Your manager</div>
                      <Text strong>{info.managerName}</Text>
                    </div>
                  </Space>
                </Card>
              )}
              {info.buddyName && (
                <Card size="small" style={{ minWidth: 200 }}>
                  <Space>
                    <TeamOutlined style={{ fontSize: 20, color: '#52c41a' }} />
                    <div>
                      <div style={{ fontSize: 11, color: '#888' }}>
                        Your {info.buddyRole?.toLowerCase() ?? 'buddy'}
                      </div>
                      <Text strong>{info.buddyName}</Text>
                    </div>
                  </Space>
                </Card>
              )}
            </Space>
          )}

          {/* ── Policy acknowledgement section ── */}
          {pendingPolicies.length > 0 && (
            <>
              <Divider style={{ margin: '4px 0' }} />
              <div>
                <Title level={5} style={{ margin: '0 0 8px' }}>
                  Policies to acknowledge
                  {unacknowledged.length > 0 && (
                    <Tag color="orange" style={{ marginLeft: 8, fontWeight: 'normal' }}>
                      {unacknowledged.length} pending
                    </Tag>
                  )}
                </Title>
                <Space direction="vertical" style={{ width: '100%' }} size={6}>
                  {pendingPolicies.map((policy) => {
                    const isAcked = acknowledgedIds.includes(policy.taskStatusId)
                    return (
                      <div
                        key={policy.taskStatusId}
                        style={{
                          display: 'flex', alignItems: 'center', gap: 10,
                          padding: '6px 0',
                          opacity: isAcked ? 0.6 : 1,
                        }}
                      >
                        {isAcked ? (
                          <CheckCircleFilled style={{ color: '#52c41a', fontSize: 18, flexShrink: 0 }} />
                        ) : (
                          <Button
                            size="small"
                            type="primary"
                            loading={ackingId === policy.taskStatusId}
                            onClick={() => setAckModal(policy)}
                            style={{ flexShrink: 0 }}
                          >
                            Acknowledge
                          </Button>
                        )}
                        <Text style={{ flex: 1 }}>{policy.title}</Text>
                        <Tag color={policy.taskType === 'POLICY_ACKNOWLEDGEMENT' ? 'blue' : 'orange'}>
                          {policy.taskType === 'POLICY_ACKNOWLEDGEMENT' ? 'Policy' : 'Contract'}
                        </Tag>
                      </div>
                    )
                  })}
                </Space>
                {justAcknowledged.length > 0 && unacknowledged.length === 0 && (
                  <Alert
                    type="success"
                    message="All policies acknowledged — thank you!"
                    style={{ marginTop: 8 }}
                    showIcon
                  />
                )}
              </div>
              <Divider style={{ margin: '4px 0' }} />
            </>
          )}

          {/* ── Form intro ── */}
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            We need a few details before your first day. Your responses go
            straight to HR — they'll review and finalise your record.
            Link valid until <Text strong>{dayjs(info.expiresAt).format('YYYY-MM-DD')}</Text>.
          </Paragraph>

          <Steps current={step} size="small" items={sections.map(s => ({ title: s.title }))} />

          <SectionForm
            key={currentSection.key}
            section={currentSection}
            initial={data[currentSection.key]}
            onSubmit={(values) => {
              const collected: Record<string, unknown> = { ...data, [currentSection.key]: values }
              setData(collected)
              if (step < sections.length - 1) {
                setStep(step + 1)
              } else {
                const payload: Record<string, unknown> = {}
                for (const [k, v] of Object.entries(collected)) {
                  if (v == null) continue
                  if (Array.isArray(v) && v.length === 0) continue
                  if (typeof v === 'object' && Object.keys(v as object).length === 0) continue
                  payload[k] = v
                }
                setSubmitting(true)
                publicPreboardingApi.submit(token!, payload)
                  .then(() => setDone(true))
                  .catch((e) => message.error(e?.response?.data?.message ?? 'Submit failed'))
                  .finally(() => setSubmitting(false))
              }
            }}
            onBack={step > 0 ? () => setStep(step - 1) : undefined}
            isLast={step === sections.length - 1}
            submitting={submitting}
          />
        </Space>
      </Card>

      {/* ── Acknowledgement confirmation modal ── */}
      <Modal
        open={ackModal != null}
        title="Acknowledge policy"
        okText="I acknowledge"
        cancelText="Cancel"
        confirmLoading={ackingId != null}
        onOk={() => ackModal && doAcknowledge(ackModal)}
        onCancel={() => setAckModal(null)}
      >
        {ackModal && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Paragraph>
              Please confirm that you have read and understood the following:
            </Paragraph>
            <Text strong>{ackModal.title}</Text>
            <Paragraph type="secondary" style={{ marginBottom: 0 }}>
              By clicking <Text strong>I acknowledge</Text>, you confirm you have read and accept
              this {ackModal.taskType === 'POLICY_ACKNOWLEDGEMENT' ? 'policy' : 'contract'}.
            </Paragraph>
          </Space>
        )}
      </Modal>
    </div>
  )

  // silence unused
  void justAcknowledged
}

// ────────────────────────────────────────────────────────────────────────────
// Per-section form
// ────────────────────────────────────────────────────────────────────────────

function SectionForm({
  section, initial, onSubmit, onBack, isLast, submitting,
}: {
  section: FormSection
  initial?: unknown
  onSubmit: (values: unknown) => void
  onBack?: () => void
  isLast: boolean
  submitting: boolean
}) {
  if (section.multiple) {
    return (
      <MultiRowForm
        section={section}
        initial={(initial as Record<string, unknown>[]) ?? []}
        onSubmit={onSubmit}
        onBack={onBack}
        isLast={isLast}
        submitting={submitting}
      />
    )
  }
  return (
    <SingleForm
      section={section}
      initial={(initial as Record<string, unknown>) ?? {}}
      onSubmit={onSubmit}
      onBack={onBack}
      isLast={isLast}
      submitting={submitting}
    />
  )
}

function renderField(field: FormSection['fields'][number]) {
  switch (field.type) {
    case 'string':
    case 'email':
      return <Input />
    case 'text':
      return <Input.TextArea rows={3} />
    case 'date':
      return <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
    case 'boolean':
      return <Checkbox>Yes</Checkbox>
    case 'enum':
      return (
        <Select
          allowClear
          placeholder="Select"
          options={(field.options ?? []).map((v) => ({ value: v, label: v }))}
        />
      )
    default:
      return <Input />
  }
}

function SingleForm({
  section, initial, onSubmit, onBack, isLast, submitting,
}: {
  section: FormSection
  initial: Record<string, unknown>
  onSubmit: (values: unknown) => void
  onBack?: () => void
  isLast: boolean
  submitting: boolean
}) {
  const [form] = Form.useForm()

  // Normalise dates and submit.
  const finish = (values: Record<string, unknown>) => {
    const cleaned: Record<string, unknown> = {}
    for (const f of section.fields) {
      const v = values[f.key]
      if (v == null) continue
      if (f.type === 'date' && v && typeof v === 'object') {
        cleaned[f.key] = dayjs(v as any).format('YYYY-MM-DD')
      } else if (typeof v === 'string' && v.trim() === '') {
        continue
      } else {
        cleaned[f.key] = v
      }
    }
    onSubmit(cleaned)
  }

  return (
    <Form form={form} layout="vertical" initialValues={initial} onFinish={finish}>
      {section.fields.map((f) => (
        <Form.Item
          key={f.key}
          name={f.key}
          label={f.label}
          rules={f.required ? [{ required: true, message: `${f.label} is required` }] : []}
          valuePropName={f.type === 'boolean' ? 'checked' : 'value'}
        >
          {renderField(f)}
        </Form.Item>
      ))}
      <Space>
        {onBack && <Button onClick={onBack}>Back</Button>}
        <Button type="primary" htmlType="submit" loading={submitting}>
          {isLast ? 'Submit' : 'Next'}
        </Button>
      </Space>
    </Form>
  )
}

function MultiRowForm({
  section, initial, onSubmit, onBack, isLast, submitting,
}: {
  section: FormSection
  initial: Record<string, unknown>[]
  onSubmit: (values: unknown) => void
  onBack?: () => void
  isLast: boolean
  submitting: boolean
}) {
  const [rows, setRows] = useState<Record<string, unknown>[]>(initial.length ? initial : [])

  const addRow = () => setRows([...rows, {}])
  const removeRow = (i: number) => setRows(rows.filter((_, idx) => idx !== i))
  const updateRow = (i: number, key: string, v: unknown) =>
    setRows(rows.map((r, idx) => (idx === i ? { ...r, [key]: v } : r)))

  const finish = () => {
    // Normalise: drop empty rows, format dates.
    const cleaned: Record<string, unknown>[] = []
    for (const row of rows) {
      const out: Record<string, unknown> = {}
      let anyValue = false
      for (const f of section.fields) {
        const v = row[f.key]
        if (v == null || (typeof v === 'string' && v.trim() === '')) continue
        if (f.type === 'date' && typeof v === 'object') {
          out[f.key] = dayjs(v as any).format('YYYY-MM-DD')
        } else {
          out[f.key] = v
        }
        anyValue = true
      }
      // Validate required.
      for (const f of section.fields) {
        if (f.required && (out[f.key] == null || out[f.key] === '')) {
          // Skip row if not even the required fields are present.
          if (!anyValue) break
        }
      }
      if (anyValue) cleaned.push(out)
    }
    onSubmit(cleaned)
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      {rows.length === 0 && (
        <Alert
          type="info"
          message={`No ${section.title.toLowerCase()} yet`}
          description="Optional — leave empty if not applicable, or click Add row."
        />
      )}
      {rows.map((row, i) => (
        <Card size="small" key={i} title={`#${i + 1}`} extra={
          <a onClick={() => removeRow(i)} style={{ color: 'red' }}>Remove</a>
        }>
          {section.fields.map((f) => {
            const v = row[f.key]
            const id = `${section.key}-${i}-${f.key}`
            return (
              <div key={f.key} style={{ marginBottom: 8 }}>
                <div style={{ fontSize: 11, color: '#888' }}>
                  {f.label}{f.required && <span style={{ color: 'red' }}> *</span>}
                </div>
                {f.type === 'enum' ? (
                  <Select
                    id={id}
                    allowClear
                    style={{ width: '100%' }}
                    value={v as string}
                    onChange={(val) => updateRow(i, f.key, val)}
                    options={(f.options ?? []).map((o) => ({ value: o, label: o }))}
                  />
                ) : f.type === 'date' ? (
                  <DatePicker
                    style={{ width: '100%' }}
                    value={v ? dayjs(v as any) : null}
                    onChange={(d) => updateRow(i, f.key, d)}
                    format="YYYY-MM-DD"
                  />
                ) : f.type === 'boolean' ? (
                  <Checkbox checked={!!v} onChange={(e) => updateRow(i, f.key, e.target.checked)}>
                    Yes
                  </Checkbox>
                ) : f.type === 'text' ? (
                  <Input.TextArea
                    rows={2}
                    value={v as string ?? ''}
                    onChange={(e) => updateRow(i, f.key, e.target.value)}
                  />
                ) : (
                  <Input
                    value={v as string ?? ''}
                    onChange={(e) => updateRow(i, f.key, e.target.value)}
                  />
                )}
              </div>
            )
          })}
        </Card>
      ))}
      <Button onClick={addRow}>+ Add row</Button>
      <Space>
        {onBack && <Button onClick={onBack}>Back</Button>}
        <Button type="primary" onClick={finish} loading={submitting}>
          {isLast ? 'Submit' : 'Next'}
        </Button>
      </Space>
    </Space>
  )
}
