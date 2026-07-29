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
  Switch,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  recruitmentApi,
  type HiringReason,
  type RequisitionType,
  type Vacancy,
  type VacancyRequest,
} from '../api/recruitment'
import { positionsApi, type Position } from '../api/positions'
import { EmployeePicker } from '../components/EmployeePicker'
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
  // M274 — requisition fields
  requisitionType?: RequisitionType
  hiringReason?: HiringReason
  targetStartDate?: dayjs.Dayjs
  costCentre?: string
  employmentType?: string
  replacedEmployeeId?: string
  // M277 — confidential requisition
  confidential?: boolean
}

// M274 — requisition taxonomies (Recruitment PRD §4). Labels are
// humanised here; values stay as the backend enum names.
const REQ_TYPE_OPTIONS: { value: RequisitionType; label: string }[] = [
  { value: 'NEW_HEADCOUNT', label: 'New headcount' },
  { value: 'REPLACEMENT', label: 'Replacement' },
  { value: 'TEMPORARY', label: 'Temporary' },
  { value: 'PROJECT', label: 'Project hire' },
  { value: 'SEASONAL', label: 'Seasonal' },
  { value: 'INTERNSHIP', label: 'Internship' },
  { value: 'CONTRACTOR', label: 'Contractor' },
  { value: 'MASS_HIRING', label: 'Mass hiring' },
  { value: 'EXECUTIVE', label: 'Executive' },
  { value: 'INTERNAL', label: 'Internal hiring' },
]

const HIRING_REASON_OPTIONS: { value: HiringReason; label: string }[] = [
  { value: 'NEW_POSITION', label: 'New position' },
  { value: 'RESIGNATION', label: 'Resignation' },
  { value: 'TERMINATION', label: 'Termination' },
  { value: 'RETIREMENT', label: 'Retirement' },
  { value: 'TRANSFER', label: 'Transfer' },
  { value: 'PROMOTION', label: 'Promotion' },
  { value: 'DEPARTMENT_EXPANSION', label: 'Department expansion' },
  { value: 'NEW_BRANCH', label: 'New branch opening' },
  { value: 'NEW_PROJECT', label: 'New project' },
  { value: 'SEASONAL_DEMAND', label: 'Seasonal demand' },
  { value: 'BUSINESS_GROWTH', label: 'Business growth' },
  { value: 'COMPLIANCE_REQUIREMENT', label: 'Compliance requirement' },
  { value: 'WORKLOAD_INCREASE', label: 'Workload increase' },
  { value: 'OTHER', label: 'Other' },
]

export function VacancyFormPage() {
  const { id } = useParams()
  const editing = !!id
  // M242 — When the Position Control page drops a "Create vacancy"
  // shortcut, it passes `?positionId=<uuid>&openings=<gap>` so the
  // recruiter doesn't have to re-pick the position or recompute the
  // gap. The Form-level prefill effect below picks it up.
  const [searchParams] = useSearchParams()
  const prefilledPositionId = searchParams.get('positionId')
  const prefilledOpenings = Number(searchParams.get('openings')) || 1
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [positions, setPositions] = useState<Position[]>([])
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    positionsApi.list({ size: 500 }).then((p) => setPositions(p.content))
  }, [])

  useEffect(() => {
    if (!editing) {
      form.setFieldsValue({
        openings: prefilledOpenings,
        currency: 'AZN',
        requisitionType: 'NEW_HEADCOUNT',
        // ?positionId arrives ahead of the positions list, so just
        // seed the form field now and let the second effect below
        // fill in title / department / salary once positions load.
        positionId: prefilledPositionId ?? undefined,
      })
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
          // M274 — requisition fields
          requisitionType: v.requisitionType,
          hiringReason: v.hiringReason ?? undefined,
          targetStartDate: v.targetStartDate ? dayjs(v.targetStartDate) : undefined,
          costCentre: v.costCentre ?? undefined,
          employmentType: v.employmentType ?? undefined,
          replacedEmployeeId: v.replacedEmployeeId ?? undefined,
          confidential: v.confidential,
        })
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load vacancy'))
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  // M242 — Once the positions list arrives, run the same prefill the
  // user would get by picking the position manually. Skipped in edit
  // mode (existing vacancy already has its fields).
  useEffect(() => {
    if (editing || !prefilledPositionId || positions.length === 0) return
    onPositionChange(prefilledPositionId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [positions, prefilledPositionId, editing])

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
      // M274 — requisition fields
      requisitionType: v.requisitionType,
      hiringReason: v.hiringReason,
      targetStartDate: v.targetStartDate?.format('YYYY-MM-DD'),
      costCentre: v.costCentre,
      employmentType: v.employmentType,
      replacedEmployeeId: v.replacedEmployeeId,
      // M277 — confidential requisition
      confidential: v.confidential ?? false,
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
                <EmployeePicker allowClear placeholder="Select hiring manager" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="recruiterId" label="Recruiter">
                <EmployeePicker allowClear placeholder="Select recruiter" />
              </Form.Item>
            </Col>
          </Row>
          {/* M274 — requisition fields (Recruitment PRD §4) */}
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="requisitionType"
                label="Requisition type"
                rules={[{ required: true }]}
              >
                <Select options={REQ_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="hiringReason" label="Hiring reason">
                <Select allowClear options={HIRING_REASON_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="targetStartDate" label="Target start date">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="costCentre" label="Cost centre" rules={[{ max: 64 }]}>
                <Input placeholder="e.g. CC-FIN" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="employmentType" label="Employment type" rules={[{ max: 32 }]}>
                <Input placeholder="e.g. FULL_TIME" />
              </Form.Item>
            </Col>
            <Col span={8}>
              {/* Only meaningful for REPLACEMENT requisitions; harmless
                  to leave visible — backend stores it regardless. */}
              <Form.Item
                noStyle
                shouldUpdate={(prev, cur) => prev.requisitionType !== cur.requisitionType}
              >
                {({ getFieldValue }) =>
                  getFieldValue('requisitionType') === 'REPLACEMENT' ? (
                    <Form.Item name="replacedEmployeeId" label="Replacing employee">
                      <EmployeePicker allowClear placeholder="Who is being backfilled?" />
                    </Form.Item>
                  ) : null
                }
              </Form.Item>
            </Col>
          </Row>
          {/* M277 — confidential requisition (Recruitment PRD §41) */}
          <Form.Item
            name="confidential"
            label="Confidential requisition"
            valuePropName="checked"
            tooltip="Visible only to the named recruiter, hiring manager, and HR admins. Use for executive hiring or replacing an employee who doesn't know yet."
          >
            <Switch />
          </Form.Item>
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
