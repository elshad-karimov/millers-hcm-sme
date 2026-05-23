import { useEffect, useState } from 'react'
import {
  Button,
  Col,
  DatePicker,
  Form,
  Row,
  Select,
  Space,
  Spin,
  Typography,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate, useParams } from 'react-router-dom'
import { attendanceApi, type WorkSchedule } from '../api/attendance'
import { employeesApi, type Employee } from '../api/employees'
import { FormPageShell } from '../components/FormPageShell'

interface FormValues {
  employeeId: string
  effectiveFrom: dayjs.Dayjs
  effectiveTo?: dayjs.Dayjs
}

const LIST_PATH = '/attendance/schedules'

export function ScheduleAssignmentFormPage() {
  const { scheduleId } = useParams()
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [schedule, setSchedule] = useState<WorkSchedule | null>(null)
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    Promise.all([attendanceApi.schedules(), employeesApi.list({ size: 500 })])
      .then(([schedules, employeesRes]) => {
        setSchedule(schedules.find((s) => s.id === scheduleId) ?? null)
        setEmployees(employeesRes.content)
      })
      .catch((err) => message.error(err?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [scheduleId, message])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    try {
      await attendanceApi.assign({
        employeeId: v.employeeId,
        scheduleId: scheduleId!,
        effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
        effectiveTo: v.effectiveTo?.format('YYYY-MM-DD'),
      })
      message.success('Assignment created')
      navigate(LIST_PATH)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Assignment failed',
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <FormPageShell
      title={`Assign schedule${schedule ? ` ${schedule.code}` : ''}`}
      backTo={LIST_PATH}
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 560 }}>
          <Typography.Paragraph type="secondary">
            Schedule: <b>{schedule?.code}</b> — {schedule?.name} (
            {schedule?.workStart?.slice(0, 5)}–{schedule?.workEnd?.slice(0, 5)})
          </Typography.Paragraph>
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Select an employee' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
            />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="effectiveFrom"
                label="Effective from"
                rules={[{ required: true, message: 'Required' }]}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="effectiveTo" label="Effective to (optional)">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                Assign
              </Button>
            </Space>
          </Form.Item>
        </Form>
      )}
    </FormPageShell>
  )
}
