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
  Spin,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate, useParams } from 'react-router-dom'
import { recruitmentApi, type Vacancy, type VacancyRequest } from '../api/recruitment'
import { positionsApi, type Position } from '../api/positions'
import { employeesApi, type Employee } from '../api/employees'
import { FormPageShell } from '../components/FormPageShell'

const LIST_PATH = '/recruitment/vacancies'

interface FormValues {
  title: string
  positionId?: string
  department?: string
  location?: string
  openings: number
  description?: string
  requirements?: string
  salaryMin?: number
  salaryMax?: number
  currency: string
  hiringManagerId?: string
  recruiterId?: string
  range?: [dayjs.Dayjs | undefined, dayjs.Dayjs | undefined]
}

export function VacancyFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [positions, setPositions] = useState<Position[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    Promise.all([
      positionsApi.list({ size: 500 }),
      employeesApi.list({ size: 500 }),
    ]).then(([p, e]) => {
      setPositions(p.content)
      setEmployees(e.content)
    })
  }, [])

  useEffect(() => {
    if (!editing) {
      form.setFieldsValue({ openings: 1, currency: 'AZN' })
      return
    }
    setLoading(true)
    recruitmentApi
      .vacancy(id!)
      .then((v: Vacancy) => {
        form.setFieldsValue({
          title: v.title,
          positionId: v.positionId ?? undefined,
          department: v.department ?? undefined,
          location: v.location ?? undefined,
          openings: v.openings,
          description: v.description ?? undefined,
          requirements: v.requirements ?? undefined,
          salaryMin: v.salaryMin ?? undefined,
          salaryMax: v.salaryMax ?? undefined,
          currency: v.currency,
          hiringManagerId: v.hiringManagerId ?? undefined,
          recruiterId: v.recruiterId ?? undefined,
          range: [
            v.openingDate ? dayjs(v.openingDate) : undefined,
            v.closingDate ? dayjs(v.closingDate) : undefined,
          ],
        })
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load vacancy'))
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  const onPositionChange = (positionId?: string) => {
    if (!positionId) return
    const p = positions.find((x) => x.id === positionId)
    if (!p) return
    const current = form.getFieldsValue()
    form.setFieldsValue({
      title: current.title || p.title,
      department: current.department || p.orgUnitLabel || undefined,
      location: current.location || p.location || undefined,
      salaryMin: current.salaryMin ?? p.salaryMin ?? undefined,
      salaryMax: current.salaryMax ?? p.salaryMax ?? undefined,
      currency: current.currency || p.currency,
    })
  }

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const payload: VacancyRequest = {
      title: v.title,
      positionId: v.positionId,
      department: v.department,
      location: v.location,
      openings: v.openings,
      description: v.description,
      requirements: v.requirements,
      salaryMin: v.salaryMin,
      salaryMax: v.salaryMax,
      currency: v.currency,
      hiringManagerId: v.hiringManagerId,
      recruiterId: v.recruiterId,
      openingDate: v.range?.[0]?.format('YYYY-MM-DD'),
      closingDate: v.range?.[1]?.format('YYYY-MM-DD'),
    }
    try {
      if (editing) {
        await recruitmentApi.updateVacancy(id!, payload)
        message.success('Vacancy updated')
      } else {
        await recruitmentApi.createVacancy(payload)
        message.success('Vacancy created')
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
    <FormPageShell title={editing ? 'Edit vacancy' : 'New vacancy'} backTo={LIST_PATH}>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 760 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="title" label="Title" rules={[{ required: true, max: 200 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="openings" label="Openings" rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="currency" label="Currency">
                <Input maxLength={3} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="positionId" label="Linked staffing position">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="(unlinked)"
              options={positions.map((p) => ({
                value: p.id,
                label: `${p.code} — ${p.title}${p.orgUnitLabel ? ` · ${p.orgUnitLabel}` : ''}`,
              }))}
              onChange={onPositionChange}
            />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="department" label="Department" rules={[{ max: 160 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="location" label="Location" rules={[{ max: 160 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="salaryMin" label="Salary min">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="salaryMax" label="Salary max">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="hiringManagerId" label="Hiring manager">
                <Select
                  allowClear
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
              <Form.Item name="recruiterId" label="Recruiter">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  options={employees.map((e) => ({
                    value: e.id,
                    label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="range" label="Opening / closing dates">
            <DatePicker.RangePicker style={{ width: '100%', maxWidth: 360 }} />
          </Form.Item>
          <Form.Item name="description" label="Job description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="requirements" label="Requirements">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create vacancy'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      )}
    </FormPageShell>
  )
}
