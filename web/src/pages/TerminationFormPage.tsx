import { useEffect, useState } from 'react'
import {
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  Row,
  Select,
  Space,
  Typography,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate } from 'react-router-dom'
import {
  lifecycleApi,
  type TerminationReason,
  type TerminationSubmitRequest,
} from '../api/lifecycle'
import { employeesApi, type Employee } from '../api/employees'
import { FormPageShell } from '../components/FormPageShell'

const LIST_PATH = '/lifecycle/terminations'

const REASONS: { value: TerminationReason; label: string }[] = [
  { value: 'VOLUNTARY_RESIGNATION', label: 'Voluntary resignation' },
  { value: 'INVOLUNTARY_DISMISSAL', label: 'Involuntary dismissal' },
  { value: 'MUTUAL_AGREEMENT', label: 'Mutual agreement' },
  { value: 'END_OF_CONTRACT', label: 'End of contract' },
  { value: 'RETIREMENT', label: 'Retirement' },
  { value: 'REDUNDANCY', label: 'Redundancy' },
  { value: 'PROBATION_FAIL', label: 'Probation fail' },
  { value: 'DEATH', label: 'Death' },
  { value: 'OTHER', label: 'Other' },
]

interface FormValues {
  employeeId: string
  reasonCode: TerminationReason
  reasonDetail?: string
  noticeDate: dayjs.Dayjs
  lastWorkingDate: dayjs.Dayjs
  effectiveDate: dayjs.Dayjs
  attachmentUrls?: string
  note?: string
}

export function TerminationFormPage() {
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [employees, setEmployees] = useState<Employee[]>([])
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content))
  }, [])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const payload: TerminationSubmitRequest = {
      employeeId: v.employeeId,
      reasonCode: v.reasonCode,
      reasonDetail: v.reasonDetail,
      noticeDate: v.noticeDate.format('YYYY-MM-DD'),
      lastWorkingDate: v.lastWorkingDate.format('YYYY-MM-DD'),
      effectiveDate: v.effectiveDate.format('YYYY-MM-DD'),
      attachmentUrls: v.attachmentUrls,
      note: v.note,
    }
    try {
      await lifecycleApi.submitTermination(payload)
      message.success('Termination submitted — workflow started')
      navigate(LIST_PATH)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Submission failed',
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <FormPageShell title="New termination" backTo={LIST_PATH}>
      <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 760 }}>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
              <Select
                showSearch
                optionFilterProp="label"
                options={employees.map((e) => ({
                  value: e.id,
                  label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
                }))}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="reasonCode" label="Reason" rules={[{ required: true }]}>
              <Select options={REASONS} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name="noticeDate" label="Notice date" rules={[{ required: true }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item
              name="lastWorkingDate"
              label="Last working date"
              rules={[{ required: true }]}
            >
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="effectiveDate" label="Effective (end) date" rules={[{ required: true }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="reasonDetail" label="Reason detail">
          <Input.TextArea rows={3} />
        </Form.Item>
        <Form.Item
          name="attachmentUrls"
          label="Attachments"
          tooltip="One URL per line (until MinIO uploads ship)."
        >
          <Input.TextArea rows={2} placeholder="https://…/resignation-letter.pdf" />
        </Form.Item>
        <Form.Item name="note" label="Note">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
            <Button type="primary" htmlType="submit" loading={saving}>
              Submit termination
            </Button>
          </Space>
        </Form.Item>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          Submitting starts the <code>TERMINATION_APPROVAL</code> workflow (Manager → HR / Legal →
          Executive). After approval, the HR specialist completes clearances and processes the
          termination — Employee status flips to TERMINATED, the position is freed, and an unused
          leave + severance settlement is snapshotted.
        </Typography.Text>
      </Form>
    </FormPageShell>
  )
}
