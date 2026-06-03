import { useEffect, useState } from 'react'
import {
  Button,
  Checkbox,
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
  businessTripApi,
  type BusinessTripSubmitRequest,
  type TripType,
} from '../api/businessTrip'
import { employeesApi, type Employee } from '../api/employees'
import { selfApi } from '../api/self'
import { useAuth } from '../auth/AuthContext'
import { FormPageShell } from '../components/FormPageShell'
import { RoleSets } from '../auth/roleSets'

const LIST_PATH = '/business-trips'

interface FormValues {
  employeeId: string
  tripType: TripType
  destinationCountry?: string
  destinationCity: string
  purpose?: string
  project?: string
  costCentre?: string
  range: [dayjs.Dayjs, dayjs.Dayjs]
  currency?: string
  dailyAllowance?: number
  requestedAdvance?: number
  mealsProvided?: boolean
  accommodationProvided?: boolean
  attachmentUrls?: string
}

export function BusinessTripFormPage() {
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const [form] = Form.useForm<FormValues>()
  const isHrMode = hasRole(...RoleSets.HR_PLUS_MANAGERS_READ)
  const [employees, setEmployees] = useState<Employee[]>([])
  const [selfLabel, setSelfLabel] = useState<string>('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    form.setFieldsValue({ tripType: 'DOMESTIC', currency: 'AZN' })
    if (isHrMode) {
      employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content))
    } else {
      selfApi.profile().then((p) => {
        setSelfLabel(`${p.firstName} ${p.lastName} (${p.employeeNo})`)
        form.setFieldsValue({ employeeId: p.id })
      })
    }
  }, [isHrMode, form])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const payload: BusinessTripSubmitRequest = {
      employeeId: v.employeeId,
      tripType: v.tripType,
      destinationCountry: v.destinationCountry,
      destinationCity: v.destinationCity,
      purpose: v.purpose,
      project: v.project,
      costCentre: v.costCentre,
      startDate: v.range[0].format('YYYY-MM-DD'),
      endDate: v.range[1].format('YYYY-MM-DD'),
      currency: v.currency,
      dailyAllowance: v.dailyAllowance,
      requestedAdvance: v.requestedAdvance,
      mealsProvided: v.mealsProvided,
      accommodationProvided: v.accommodationProvided,
      attachmentUrls: v.attachmentUrls,
    }
    try {
      if (isHrMode) {
        await businessTripApi.submit(payload)
      } else {
        await selfApi.submitBusinessTrip(payload)
      }
      message.success('Business trip submitted — workflow started')
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
    <FormPageShell title="New business trip" backTo={LIST_PATH}>
      <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 760 }}>
        <Row gutter={16}>
          <Col span={12}>
            {isHrMode ? (
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
            ) : (
              <>
                <Form.Item label="Employee">
                  <Input disabled value={selfLabel} />
                </Form.Item>
                <Form.Item name="employeeId" hidden><Input /></Form.Item>
              </>
            )}
          </Col>
          <Col span={12}>
            <Form.Item name="tripType" label="Trip type" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'DOMESTIC', label: 'Domestic' },
                  { value: 'INTERNATIONAL', label: 'International' },
                ]}
              />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item
              name="destinationCity"
              label="Destination city"
              rules={[{ required: true, max: 120 }]}
            >
              <Input />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="destinationCountry" label="Destination country" rules={[{ max: 80 }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item
              name="range"
              label="Dates"
              rules={[{ required: true, message: 'Pick trip dates' }]}
            >
              <DatePicker.RangePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="purpose" label="Purpose">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name="project" label="Project / cost code" rules={[{ max: 120 }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="costCentre" label="Cost centre" rules={[{ max: 64 }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="currency" label="Currency">
              <Input maxLength={3} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="dailyAllowance" label="Daily allowance">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="requestedAdvance" label="Requested advance">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="mealsProvided" valuePropName="checked">
              <Checkbox>Meals provided by employer</Checkbox>
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="accommodationProvided" valuePropName="checked">
              <Checkbox>Accommodation provided</Checkbox>
            </Form.Item>
          </Col>
        </Row>
        <Form.Item
          name="attachmentUrls"
          label="Attachments"
          tooltip="One URL per line (until MinIO uploads ship)."
        >
          <Input.TextArea rows={2} placeholder="https://…/flight-ticket.pdf" />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
            <Button type="primary" htmlType="submit" loading={saving}>
              Submit trip request
            </Button>
          </Space>
        </Form.Item>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          Submitting starts the <code>BUSINESS_TRIP_APPROVAL</code> workflow (Manager → Finance / HR
          → Executive). Final expense reconciliation happens after the trip on the trip's detail page.
        </Typography.Text>
      </Form>
    </FormPageShell>
  )
}
