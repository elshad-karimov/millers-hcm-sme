import { useEffect, useState } from 'react'
import {
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
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
  type ChangeType,
  type ContractChangeSubmitRequest,
} from '../api/lifecycle'
import { positionsApi, type Position } from '../api/positions'
import { EmployeePicker } from '../components/EmployeePicker'
import { FormPageShell } from '../components/FormPageShell'

const LIST_PATH = '/lifecycle/contract-changes'

const TYPES: { value: ChangeType; label: string; help: string }[] = [
  { value: 'SALARY', label: 'Salary', help: 'Adds an effective-dated row in payroll.employee_compensation' },
  { value: 'POSITION', label: 'Position', help: 'Moves the employee to a different staffing position (and swaps occupancy)' },
  { value: 'DEPARTMENT', label: 'Department', help: 'Updates department / org unit on the employee' },
  { value: 'MANAGER', label: 'Manager', help: 'Updates direct manager' },
  { value: 'JOB_TITLE', label: 'Job title', help: 'Updates display title on the employee record' },
  { value: 'COST_CENTRE', label: 'Cost centre', help: 'Updates cost centre attribution' },
  { value: 'GRADE', label: 'Grade', help: 'Records grade change (stored as JSON, not applied directly to employee)' },
  { value: 'LOCATION', label: 'Location', help: 'Records location change (stored as JSON, not applied to employee)' },
  { value: 'EMPLOYMENT_TYPE', label: 'Employment type', help: 'Records employment-type change (stored as JSON)' },
]

interface FormValues {
  employeeId: string
  changeType: ChangeType
  effectiveDate: dayjs.Dayjs
  reason?: string
  monthlyBaseSalary?: number
  currency?: string
  positionId?: string
  departmentName?: string
  managerId?: string
  positionTitle?: string
  costCentre?: string
  grade?: string
  location?: string
  employmentType?: string
  attachmentUrls?: string
  note?: string
}

export function ContractChangeFormPage() {
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [positions, setPositions] = useState<Position[]>([])
  const [saving, setSaving] = useState(false)
  const [changeType, setChangeType] = useState<ChangeType | undefined>()

  useEffect(() => {
    positionsApi.list({ size: 500 }).then((p) => setPositions(p.content))
  }, [])

  const onFinish = async (v: FormValues) => {
    const newValue: Record<string, unknown> = {}
    switch (v.changeType) {
      case 'SALARY':
        newValue.monthlyBaseSalary = v.monthlyBaseSalary
        newValue.currency = v.currency || 'AZN'
        break
      case 'POSITION':
        newValue.positionId = v.positionId
        break
      case 'DEPARTMENT':
        newValue.departmentName = v.departmentName
        break
      case 'MANAGER':
        newValue.managerId = v.managerId
        break
      case 'JOB_TITLE':
        newValue.positionTitle = v.positionTitle
        break
      case 'COST_CENTRE':
        newValue.costCentre = v.costCentre
        break
      case 'GRADE':
        newValue.grade = v.grade
        break
      case 'LOCATION':
        newValue.location = v.location
        break
      case 'EMPLOYMENT_TYPE':
        newValue.employmentType = v.employmentType
        break
    }

    setSaving(true)
    const payload: ContractChangeSubmitRequest = {
      employeeId: v.employeeId,
      changeType: v.changeType,
      effectiveDate: v.effectiveDate.format('YYYY-MM-DD'),
      reason: v.reason,
      newValue,
      attachmentUrls: v.attachmentUrls,
      note: v.note,
    }
    try {
      await lifecycleApi.submitContractChange(payload)
      message.success('Contract change submitted — workflow started')
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

  const typeMeta = TYPES.find((t) => t.value === changeType)

  return (
    <FormPageShell title="New contract change" backTo={LIST_PATH}>
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        onValuesChange={(changed) => {
          if (changed.changeType !== undefined) setChangeType(changed.changeType)
        }}
        style={{ maxWidth: 760 }}
      >
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
              <EmployeePicker placeholder="Select employee" />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item name="changeType" label="Change type" rules={[{ required: true }]}>
              <Select options={TYPES.map((t) => ({ value: t.value, label: t.label }))} />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item name="effectiveDate" label="Effective date" rules={[{ required: true }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>

        {typeMeta && (
          <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
            {typeMeta.help}
          </Typography.Paragraph>
        )}

        {changeType === 'SALARY' && (
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="monthlyBaseSalary"
                label="New monthly base salary"
                rules={[{ required: true }]}
              >
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="currency" label="Currency" initialValue="AZN">
                <Input maxLength={3} />
              </Form.Item>
            </Col>
          </Row>
        )}
        {changeType === 'POSITION' && (
          <Form.Item name="positionId" label="New position" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={positions.map((p) => ({
                value: p.id,
                label: `${p.code} — ${p.title}${p.orgUnitLabel ? ` · ${p.orgUnitLabel}` : ''}`,
              }))}
            />
          </Form.Item>
        )}
        {changeType === 'DEPARTMENT' && (
          <Form.Item name="departmentName" label="New department" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        )}
        {changeType === 'MANAGER' && (
          <Form.Item name="managerId" label="New manager" rules={[{ required: true }]}>
            <EmployeePicker placeholder="Select manager" />
          </Form.Item>
        )}
        {changeType === 'JOB_TITLE' && (
          <Form.Item name="positionTitle" label="New job title" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        )}
        {changeType === 'COST_CENTRE' && (
          <Form.Item name="costCentre" label="New cost centre" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        )}
        {changeType === 'GRADE' && (
          <Form.Item name="grade" label="New grade" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        )}
        {changeType === 'LOCATION' && (
          <Form.Item name="location" label="New location" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        )}
        {changeType === 'EMPLOYMENT_TYPE' && (
          <Form.Item name="employmentType" label="New employment type" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        )}

        <Form.Item name="reason" label="Reason">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item
          name="attachmentUrls"
          label="Attachments"
          tooltip="One URL per line (until MinIO uploads ship)."
        >
          <Input.TextArea rows={2} placeholder="https://…/promotion-letter.pdf" />
        </Form.Item>
        <Form.Item name="note" label="Note">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
            <Button type="primary" htmlType="submit" loading={saving}>
              Submit change
            </Button>
          </Space>
        </Form.Item>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          Submitting starts the <code>CONTRACT_CHANGE_APPROVAL</code> workflow (Manager → HR →
          Finance / Executive). On approval the change is applied automatically — SALARY adds a new
          effective-dated row in payroll; other types update the employee record directly.
        </Typography.Text>
      </Form>
    </FormPageShell>
  )
}
