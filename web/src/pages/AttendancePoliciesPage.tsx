import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Divider,
  Drawer,
  Form,
  Input,
  InputNumber,
  message,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import { PlusOutlined, EditOutlined, StopOutlined, CheckOutlined } from '@ant-design/icons'
import { attendanceApi, type AttendancePolicy, type AttendancePolicyRequest } from '../api/attendance'

const { Title, Text } = Typography
const { TextArea } = Input

const DEFAULT_POLICY: Partial<AttendancePolicyRequest> = {
  clockInGraceMinutes: 10,
  clockOutGraceMinutes: 0,
  lateDeductionEnabled: false,
  lateMaxBeforeHalfDayMinutes: 120,
  lateMonthlyThresholdMinutes: 0,
  earlyLeaveDeductionEnabled: false,
  earlyLeaveTreatment: 'SALARY_DEDUCTION',
  earlyLeaveToleranceMinutes: 0,
  absenceDeductionEnabled: true,
  absenceDailyDivisor: 30,
  hoursPerDay: 8,
  unauthorizedAbsenceTreatment: 'UNPAID',
  overtimeRequiresPreapproval: false,
  overtimeDeptHeadThresholdMinutes: 120,
  compOffEnabled: false,
  autoBreakDeductionEnabled: false,
  breakMinutes: 60,
  minHoursForBreakDeduction: '6.0',
  roundingEnabled: false,
  roundingMinutes: 15,
  roundingDirection: 'NEAREST',
  active: true,
}

export function AttendancePoliciesPage() {
  const [policies, setPolicies] = useState<AttendancePolicy[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<AttendancePolicy | null>(null)
  const [form] = Form.useForm<AttendancePolicyRequest>()

  const load = useCallback(() => {
    setLoading(true)
    attendanceApi.policies()
      .then(setPolicies)
      .catch(() => message.error('Failed to load policies'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  function openCreate() {
    setEditing(null)
    form.setFieldsValue({ ...DEFAULT_POLICY } as AttendancePolicyRequest)
    setDrawerOpen(true)
  }

  function openEdit(p: AttendancePolicy) {
    setEditing(p)
    form.setFieldsValue({ ...p } as AttendancePolicyRequest)
    setDrawerOpen(true)
  }

  function closeDrawer() {
    setDrawerOpen(false)
    setEditing(null)
    form.resetFields()
  }

  function handleSubmit(values: AttendancePolicyRequest) {
    setSaving(true)
    const call = editing
      ? attendanceApi.updatePolicy(editing.id, values)
      : attendanceApi.createPolicy(values)
    call
      .then(() => { message.success(editing ? 'Policy updated' : 'Policy created'); closeDrawer(); load() })
      .catch(() => message.error('Failed to save policy'))
      .finally(() => setSaving(false))
  }

  function handleDeactivate(id: string) {
    attendanceApi.deactivatePolicy(id)
      .then(() => { message.success('Policy deactivated'); load() })
      .catch(() => message.error('Failed to deactivate'))
  }

  function handleActivate(id: string) {
    attendanceApi.activatePolicy(id)
      .then(() => { message.success('Policy activated'); load() })
      .catch(() => message.error('Failed to activate'))
  }

  const columns = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 120,
      render: (v: string) => <Text code>{v}</Text>,
    },
    {
      title: 'Name',
      dataIndex: 'name',
    },
    {
      title: 'Grace (in)',
      dataIndex: 'clockInGraceMinutes',
      width: 100,
      render: (v: number) => `${v} min`,
    },
    {
      title: 'Late deduction',
      dataIndex: 'lateDeductionEnabled',
      width: 120,
      render: (v: boolean) => <Tag color={v ? 'orange' : 'default'}>{v ? 'Enabled' : 'Off'}</Tag>,
    },
    {
      title: 'Absence deduction',
      dataIndex: 'absenceDeductionEnabled',
      width: 140,
      render: (v: boolean, r: AttendancePolicy) =>
        v ? <Tag color="red">salary / {r.absenceDailyDivisor}</Tag> : <Tag>Off</Tag>,
    },
    {
      title: 'OT pre-approval',
      dataIndex: 'overtimeRequiresPreapproval',
      width: 130,
      render: (v: boolean) => <Tag color={v ? 'blue' : 'default'}>{v ? 'Required' : 'Auto'}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) => <Tag color={v ? 'green' : 'red'}>{v ? 'Active' : 'Inactive'}</Tag>,
    },
    {
      title: '',
      width: 120,
      render: (_: unknown, r: AttendancePolicy) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)} />
          {r.active ? (
            <Popconfirm title="Deactivate this policy?" onConfirm={() => handleDeactivate(r.id)}>
              <Button size="small" danger icon={<StopOutlined />} />
            </Popconfirm>
          ) : (
            <Button size="small" icon={<CheckOutlined />} onClick={() => handleActivate(r.id)} />
          )}
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>Attendance Policies</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          New Policy
        </Button>
      </div>

      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        Deduction formulas: late/early = salary / divisor / (hours × 60) per minute. Absence = salary / divisor per day.
        Resolution order: dept + type &gt; dept only &gt; type only &gt; catch-all (no filters).
      </Text>

      <Table
        rowKey="id"
        dataSource={policies}
        columns={columns}
        loading={loading}
        size="small"
        pagination={{ pageSize: 20 }}
      />

      <Drawer
        title={editing ? `Edit Policy — ${editing.code}` : 'New Attendance Policy'}
        open={drawerOpen}
        onClose={closeDrawer}
        width={640}
        footer={
          <Space style={{ float: 'right' }}>
            <Button onClick={closeDrawer}>Cancel</Button>
            <Button type="primary" onClick={() => form.submit()} loading={saving}>
              {editing ? 'Save' : 'Create'}
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item label="Code" name="code" rules={[{ required: true }]}>
            <Input placeholder="STANDARD" />
          </Form.Item>
          <Form.Item label="Name" name="name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Description" name="description">
            <TextArea rows={2} />
          </Form.Item>

          <Divider />
          <Title level={5}>Applicability (leave blank = applies to all)</Title>
          <Form.Item label="Employment Type" name="employmentType">
            <Select allowClear placeholder="All types">
              <Select.Option value="PERMANENT">Permanent</Select.Option>
              <Select.Option value="CONTRACTOR">Contractor</Select.Option>
              <Select.Option value="PART_TIME">Part Time</Select.Option>
              <Select.Option value="TEMPORARY">Temporary</Select.Option>
              <Select.Option value="INTERN">Intern</Select.Option>
            </Select>
          </Form.Item>

          <Divider />
          <Title level={5}>Grace Periods</Title>
          <Form.Item label="Clock-in grace (minutes)" name="clockInGraceMinutes">
            <InputNumber min={0} max={120} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Clock-out grace (minutes)" name="clockOutGraceMinutes">
            <InputNumber min={0} max={60} style={{ width: '100%' }} />
          </Form.Item>

          <Divider />
          <Title level={5}>Late Deduction (per-minute = salary / divisor / hours / 60)</Title>
          <Form.Item label="Enable late deduction" name="lateDeductionEnabled" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="Max late before half-day (minutes)" name="lateMaxBeforeHalfDayMinutes">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Monthly accumulation threshold (minutes, 0 = every minute counts)" name="lateMonthlyThresholdMinutes">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          <Divider />
          <Title level={5}>Early Leave</Title>
          <Form.Item label="Enable early-leave deduction" name="earlyLeaveDeductionEnabled" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="Treatment" name="earlyLeaveTreatment">
            <Select>
              <Select.Option value="SALARY_DEDUCTION">Salary Deduction</Select.Option>
              <Select.Option value="LEAVE_BALANCE">Deduct from Leave Balance</Select.Option>
              <Select.Option value="VIOLATION">Disciplinary Violation</Select.Option>
              <Select.Option value="IGNORE">Ignore</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="Tolerance (minutes)" name="earlyLeaveToleranceMinutes">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          <Divider />
          <Title level={5}>Absence Deduction (daily = salary / daily-divisor)</Title>
          <Form.Item label="Enable absence deduction" name="absenceDeductionEnabled" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="Daily divisor (30 = calendar days, 22 = working days)" name="absenceDailyDivisor">
            <InputNumber min={1} max={31} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Standard hours per day (for per-minute rate)" name="hoursPerDay">
            <InputNumber min={1} max={24} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Unauthorized absence treatment" name="unauthorizedAbsenceTreatment">
            <Select>
              <Select.Option value="UNPAID">Unpaid (salary deduction)</Select.Option>
              <Select.Option value="WARNING">Warning only</Select.Option>
              <Select.Option value="LEAVE_DEDUCTION">Deduct from leave balance</Select.Option>
            </Select>
          </Form.Item>

          <Divider />
          <Title level={5}>Overtime</Title>
          <Form.Item label="Require pre-approval for OT" name="overtimeRequiresPreapproval" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="Dept-head approval threshold (minutes)" name="overtimeDeptHeadThresholdMinutes">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Enable comp-off accrual" name="compOffEnabled" valuePropName="checked">
            <Switch />
          </Form.Item>

          <Divider />
          <Title level={5}>Status</Title>
          <Form.Item label="Active" name="active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
