import { useEffect, useState } from 'react'
import {
  Button,
  Checkbox,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Spin,
  TimePicker,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import customParseFormat from 'dayjs/plugin/customParseFormat'
import { useNavigate, useParams } from 'react-router-dom'
import { attendanceApi, type ScheduleType, type WorkSchedule } from '../api/attendance'
import { FormPageShell } from '../components/FormPageShell'

dayjs.extend(customParseFormat)

const TYPES: ScheduleType[] = [
  'FIVE_DAY',
  'SIX_DAY',
  'SHIFT',
  'FLEXIBLE',
  'ROTATIONAL',
  'NIGHT',
  'PART_TIME',
]
const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const LIST_PATH = '/attendance/schedules'

interface FormValues {
  code: string
  name: string
  scheduleType: ScheduleType
  workTime: [dayjs.Dayjs, dayjs.Dayjs]
  breakMinutes?: number
  gracePeriodMinutes?: number
  workDays: number[]
  overtimeThresholdMinutes?: number
  active?: boolean
}

export function ScheduleFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!editing) {
      form.setFieldsValue({
        scheduleType: 'FIVE_DAY',
        workTime: [dayjs('09:00:00', 'HH:mm:ss'), dayjs('18:00:00', 'HH:mm:ss')],
        breakMinutes: 60,
        gracePeriodMinutes: 10,
        workDays: [0, 1, 2, 3, 4],
        active: true,
      })
      return
    }
    setLoading(true)
    attendanceApi
      .schedules()
      .then((list) => {
        const s = list.find((x: WorkSchedule) => x.id === id)
        if (!s) throw new Error('Schedule not found')
        const indexes = s.workDays
          .split('')
          .map((c: string, i: number) => (c === '1' ? i : -1))
          .filter((i: number) => i >= 0)
        form.setFieldsValue({
          code: s.code,
          name: s.name,
          scheduleType: s.scheduleType,
          workTime: [dayjs(s.workStart, 'HH:mm:ss'), dayjs(s.workEnd, 'HH:mm:ss')],
          breakMinutes: s.breakMinutes,
          gracePeriodMinutes: s.gracePeriodMinutes,
          workDays: indexes,
          overtimeThresholdMinutes: s.overtimeThresholdMinutes ?? undefined,
          active: s.active,
        })
      })
      .catch((err) => message.error(err?.message ?? 'Failed to load schedule'))
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const bits = Array.from({ length: 7 }, (_, i) =>
      v.workDays.includes(i) ? '1' : '0',
    ).join('')
    const payload = {
      code: v.code,
      name: v.name,
      scheduleType: v.scheduleType,
      workStart: v.workTime[0].format('HH:mm:ss'),
      workEnd: v.workTime[1].format('HH:mm:ss'),
      breakMinutes: v.breakMinutes,
      gracePeriodMinutes: v.gracePeriodMinutes,
      workDays: bits,
      overtimeThresholdMinutes: v.overtimeThresholdMinutes,
      active: v.active,
    }
    try {
      if (editing) {
        await attendanceApi.updateSchedule(id!, payload)
        message.success('Schedule updated')
      } else {
        await attendanceApi.createSchedule(payload)
        message.success('Schedule created')
      }
      navigate(LIST_PATH)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <FormPageShell title={editing ? 'Edit schedule' : 'New schedule'} backTo={LIST_PATH}>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 720 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 64 }]}>
                <Input placeholder="e.g. STD-5DAY" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label="Name" rules={[{ required: true, max: 200 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="scheduleType" label="Type" rules={[{ required: true }]}>
                <Select options={TYPES.map((t) => ({ value: t, label: t.replace(/_/g, ' ') }))} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="workTime"
                label="Work hours"
                rules={[{ required: true, message: 'Required' }]}
              >
                <TimePicker.RangePicker format="HH:mm" minuteStep={5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="breakMinutes" label="Break (min)">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="gracePeriodMinutes" label="Grace period (min)">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="overtimeThresholdMinutes" label="OT threshold (min)">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="workDays" label="Working days" rules={[{ required: true }]}>
            <Checkbox.Group options={DAY_LABELS.map((label, value) => ({ label, value }))} />
          </Form.Item>
          <Form.Item name="active" valuePropName="checked">
            <Checkbox>Active</Checkbox>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create schedule'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      )}
    </FormPageShell>
  )
}
