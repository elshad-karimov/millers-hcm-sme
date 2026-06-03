import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Space,
  Spin,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import {
  lifecycleApi,
  type Termination,
  type TerminationStatus,
} from '../api/lifecycle'
import { employeesApi, type Employee } from '../api/employees'
import { FormPageShell } from '../components/FormPageShell'
import { AttachmentUploader } from '../components/AttachmentUploader'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const LIST_PATH = '/lifecycle/terminations'

const STATUS_COLOR: Record<TerminationStatus, string> = {
  DRAFT: 'default',
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
  PROCESSED: 'blue',
}

export function TerminationDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { message, modal } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canProcess = hasRole(...RoleSets.HR_ADMIN_WRITE)
  const canEditClearance = hasRole(...RoleSets.HR_WRITE)

  const [t, setT] = useState<Termination | null>(null)
  const [employee, setEmployee] = useState<Employee | null>(null)
  const [loading, setLoading] = useState(true)
  const [interviewOpen, setInterviewOpen] = useState(false)
  const [interviewForm] = Form.useForm()
  const [savingInterview, setSavingInterview] = useState(false)
  const [savingClearance, setSavingClearance] = useState(false)
  const [processing, setProcessing] = useState(false)

  const load = () => {
    if (!id) return
    setLoading(true)
    lifecycleApi
      .termination(id)
      .then(async (data) => {
        setT(data)
        try {
          const emp = await employeesApi.get(data.employeeId)
          setEmployee(emp)
        } catch {
          setEmployee(null)
        }
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  if (loading || !t) {
    return (
      <FormPageShell title="Termination" backTo={LIST_PATH}>
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      </FormPageShell>
    )
  }

  const updateClearance = async (
    key: 'clearanceIt' | 'clearanceHr' | 'clearanceFinance' | 'clearanceAssets',
    value: boolean,
  ) => {
    setSavingClearance(true)
    try {
      const updated = await lifecycleApi.updateClearance(t.id, { [key]: value })
      setT(updated)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Update failed',
      )
    } finally {
      setSavingClearance(false)
    }
  }

  const onProcess = () => {
    modal.confirm({
      title: 'Process termination?',
      content:
        'This flips the employee to TERMINATED, releases the staffing position, and snapshots the final-settlement payout. Cannot be undone.',
      okText: 'Process',
      okButtonProps: { danger: true },
      onOk: async () => {
        setProcessing(true)
        try {
          const updated = await lifecycleApi.processTermination(t.id)
          setT(updated)
          message.success('Termination processed')
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Processing failed',
          )
        } finally {
          setProcessing(false)
        }
      },
    })
  }

  const submitInterview = async (values: {
    overallRating?: number
    wouldRecommend?: boolean
    reasonForLeaving?: string
    feedback?: string
    improvementSuggestions?: string
  }) => {
    setSavingInterview(true)
    try {
      await lifecycleApi.recordExitInterview(t.id, values)
      message.success('Exit interview saved')
      setInterviewOpen(false)
      interviewForm.resetFields()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    } finally {
      setSavingInterview(false)
    }
  }

  return (
    <FormPageShell
      title={`${t.terminationNo} — ${employee ? `${employee.firstName} ${employee.lastName}` : 'Termination'}`}
      backTo={LIST_PATH}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%', maxWidth: 960 }}>
        <Card
          extra={
            <Space>
              <Tag color={STATUS_COLOR[t.status]} style={{ fontSize: 13 }}>
                {t.status}
              </Tag>
              {t.status === 'APPROVED' && canProcess && (
                <Button type="primary" loading={processing} onClick={onProcess} danger>
                  Process termination
                </Button>
              )}
            </Space>
          }
        >
          <Descriptions column={2} size="small">
            <Descriptions.Item label="Employee">
              {employee
                ? `${employee.employeeNo} — ${employee.firstName} ${employee.lastName}`
                : t.employeeId}
            </Descriptions.Item>
            <Descriptions.Item label="Reason">
              {t.reasonCode.replace(/_/g, ' ')}
            </Descriptions.Item>
            <Descriptions.Item label="Notice date">{t.noticeDate}</Descriptions.Item>
            <Descriptions.Item label="Last working date">{t.lastWorkingDate}</Descriptions.Item>
            <Descriptions.Item label="Effective date">{t.effectiveDate}</Descriptions.Item>
            <Descriptions.Item label="Created by">{t.createdBy ?? '—'}</Descriptions.Item>
            {t.reasonDetail && (
              <Descriptions.Item label="Detail" span={2}>
                {t.reasonDetail}
              </Descriptions.Item>
            )}
            {t.note && (
              <Descriptions.Item label="Note" span={2}>
                {t.note}
              </Descriptions.Item>
            )}
          </Descriptions>
        </Card>

        {(t.status === 'APPROVED' || t.status === 'PROCESSED') && (
          <Card
            title="Clearance checklist"
            extra={
              t.status === 'APPROVED' && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  HR records clearances before processing
                </Typography.Text>
              )
            }
          >
            <Row gutter={[16, 12]}>
              <Col span={6}>
                <Checkbox
                  checked={t.clearanceIt}
                  disabled={t.status !== 'APPROVED' || !canEditClearance || savingClearance}
                  onChange={(e) => updateClearance('clearanceIt', e.target.checked)}
                >
                  IT (laptop, accounts)
                </Checkbox>
              </Col>
              <Col span={6}>
                <Checkbox
                  checked={t.clearanceHr}
                  disabled={t.status !== 'APPROVED' || !canEditClearance || savingClearance}
                  onChange={(e) => updateClearance('clearanceHr', e.target.checked)}
                >
                  HR (badge, contract)
                </Checkbox>
              </Col>
              <Col span={6}>
                <Checkbox
                  checked={t.clearanceFinance}
                  disabled={t.status !== 'APPROVED' || !canEditClearance || savingClearance}
                  onChange={(e) => updateClearance('clearanceFinance', e.target.checked)}
                >
                  Finance (advances, expenses)
                </Checkbox>
              </Col>
              <Col span={6}>
                <Checkbox
                  checked={t.clearanceAssets}
                  disabled={t.status !== 'APPROVED' || !canEditClearance || savingClearance}
                  onChange={(e) => updateClearance('clearanceAssets', e.target.checked)}
                >
                  Assets / Facilities
                </Checkbox>
              </Col>
            </Row>
          </Card>
        )}

        {(t.status === 'APPROVED' || t.status === 'PROCESSED') && (
          <Card
            title="Exit interview"
            extra={
              <Button onClick={() => setInterviewOpen(true)}>Record exit interview</Button>
            }
          >
            <Typography.Text type="secondary">
              The exit interview is optional but recommended for voluntary terminations. Recorded
              with the current user as interviewer.
            </Typography.Text>
          </Card>
        )}

        {t.status === 'PROCESSED' && (
          <Card title="Final settlement snapshot">
            <Descriptions column={2} size="small">
              <Descriptions.Item label="Unused leave (days)">
                {t.unusedLeaveDays ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Unused leave payout">
                {t.unusedLeavePayout != null
                  ? `${t.unusedLeavePayout} ${t.currency ?? ''}`
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Severance">
                {t.severanceAmount != null
                  ? `${t.severanceAmount} ${t.currency ?? ''}`
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Final settlement">
                <Typography.Text strong>
                  {t.finalSettlementAmount != null
                    ? `${t.finalSettlementAmount} ${t.currency ?? ''}`
                    : '—'}
                </Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="Processed at">{t.processedAt ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Processed by">{t.processedBy ?? '—'}</Descriptions.Item>
            </Descriptions>
            {t.payoutCalcDetails && (
              <details style={{ marginTop: 12 }}>
                <summary>Calculation details (auditor trace)</summary>
                <pre
                  style={{
                    background: '#fafafa',
                    padding: 12,
                    borderRadius: 4,
                    fontSize: 12,
                    overflow: 'auto',
                  }}
                >
                  {JSON.stringify(t.payoutCalcDetails, null, 2)}
                </pre>
              </details>
            )}
          </Card>
        )}

        <Card title="Attachments" size="small">
          <AttachmentUploader
            ownerModule="LIFECYCLE"
            ownerEntity="TerminationRequest"
            ownerId={t.id}
          />
        </Card>
      </Space>

      <Modal
        open={interviewOpen}
        title="Record exit interview"
        onCancel={() => setInterviewOpen(false)}
        onOk={() => interviewForm.submit()}
        confirmLoading={savingInterview}
        okText="Save"
      >
        <Form
          form={interviewForm}
          layout="vertical"
          onFinish={submitInterview}
          initialValues={{ wouldRecommend: true }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="overallRating" label="Overall rating (1–5)">
                <InputNumber min={1} max={5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="wouldRecommend" valuePropName="checked" label=" ">
                <Checkbox>Would recommend the company</Checkbox>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="reasonForLeaving" label="Primary reason for leaving">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="feedback" label="General feedback">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="improvementSuggestions" label="Suggestions for improvement">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Space style={{ marginTop: 16 }}>
        <Button onClick={() => navigate(LIST_PATH)}>Back to list</Button>
      </Space>
    </FormPageShell>
  )
}
