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
  Spin,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate, useParams } from 'react-router-dom'
import { employeesApi, type Employee } from '../api/employees'
import { locationApi, type LocationResponse } from '../api/location'
import { FormPageShell } from '../components/FormPageShell'

interface FormValues {
  firstName: string
  lastName: string
  middleName?: string
  email?: string
  phone?: string
  gender?: string
  nationalId?: string
  birthDate?: dayjs.Dayjs
  hireDate: dayjs.Dayjs
  departmentName?: string
  positionTitle?: string
  costCentre?: string
  // M141 — work location
  workLocationId?: string
  // M132 — Section 1 cosmetic fields
  preferredName?: string
  placeOfBirth?: string
  bloodGroup?: string
  religion?: string
  nativeLanguage?: string
  // M133 — Section 3 contact fields
  altPhone?: string
  workEmail?: string
  workPhone?: string
  extension?: string
  deskNumber?: string
  // M134 — Section 4 employment fields
  employeeCategory?: string
  seniorityDate?: dayjs.Dayjs
}

const BLOOD_GROUPS = ['O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-']
const COMMON_LANGUAGES = [
  { value: 'az', label: 'Azerbaijani (az)' },
  { value: 'en', label: 'English (en)' },
  { value: 'ru', label: 'Russian (ru)' },
  { value: 'tr', label: 'Turkish (tr)' },
  { value: 'fa', label: 'Persian (fa)' },
  { value: 'ar', label: 'Arabic (ar)' },
]

const LIST_PATH = '/employees'

export function EmployeeFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [loading, setLoading] = useState<boolean>(editing)
  const [saving, setSaving] = useState(false)
  const [locationOptions, setLocationOptions] = useState<{ value: string; label: string }[]>([])

  useEffect(() => {
    locationApi.list(true)
      .then((locs: LocationResponse[]) =>
        setLocationOptions(locs.map((l) => ({ value: l.id, label: `${l.code} — ${l.name}` }))))
      .catch(() => {/* non-critical */})
  }, [])

  useEffect(() => {
    if (!editing) return
    setLoading(true)
    employeesApi
      .get(id!)
      .then((e: Employee) => {
        form.setFieldsValue({
          firstName: e.firstName,
          lastName: e.lastName,
          middleName: e.middleName ?? undefined,
          email: e.email ?? undefined,
          phone: e.phone ?? undefined,
          gender: e.gender ?? undefined,
          nationalId: e.nationalId ?? undefined,
          birthDate: e.birthDate ? dayjs(e.birthDate) : undefined,
          hireDate: dayjs(e.hireDate),
          departmentName: e.departmentName ?? undefined,
          positionTitle: e.positionTitle ?? undefined,
          costCentre: e.costCentre ?? undefined,
          workLocationId: e.workLocationId ?? undefined,
          // M132 — Section 1 cosmetic fields
          preferredName: e.preferredName ?? undefined,
          placeOfBirth: e.placeOfBirth ?? undefined,
          bloodGroup: e.bloodGroup ?? undefined,
          religion: e.religion ?? undefined,
          nativeLanguage: e.nativeLanguage ?? undefined,
          // M133 — Section 3 contact fields
          altPhone: e.altPhone ?? undefined,
          workEmail: e.workEmail ?? undefined,
          workPhone: e.workPhone ?? undefined,
          extension: e.extension ?? undefined,
          deskNumber: e.deskNumber ?? undefined,
          // M134 — Section 4 employment fields
          employeeCategory: e.employeeCategory ?? undefined,
          seniorityDate: e.seniorityDate ? dayjs(e.seniorityDate) : undefined,
        })
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load employee'),
      )
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const payload = {
      ...v,
      birthDate: v.birthDate?.format('YYYY-MM-DD'),
      hireDate: v.hireDate.format('YYYY-MM-DD'),
      // M134 — seniority date, optional
      seniorityDate: v.seniorityDate?.format('YYYY-MM-DD'),
    }
    try {
      if (editing) {
        await employeesApi.update(id!, payload)
        message.success('Employee updated')
      } else {
        await employeesApi.create(payload)
        message.success('Employee created')
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
    <FormPageShell title={editing ? 'Edit employee' : 'New employee'} backTo={LIST_PATH}>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 720 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="firstName"
                label="First name"
                rules={[{ required: true, max: 100 }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="lastName"
                label="Last name"
                rules={[{ required: true, max: 100 }]}
              >
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="middleName" label="Middle name" rules={[{ max: 100 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              {/* M132 — Section 1 spec field */}
              <Form.Item name="preferredName" label="Preferred name (nickname)"
                rules={[{ max: 120 }]}>
                <Input placeholder="Used on the directory + printable badge" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="email"
                label="Personal email"
                rules={[{ type: 'email', message: 'Enter a valid email' }, { max: 160 }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="phone" label="Personal phone" rules={[{ max: 32 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              {/* M133 — Section 3 spec field */}
              <Form.Item name="altPhone" label="Alternative phone" rules={[{ max: 32 }]}>
                <Input placeholder="Optional second personal number" />
              </Form.Item>
            </Col>
          </Row>
          {/* M133 — Section 3 work-contact row */}
          <Row gutter={16}>
            <Col span={9}>
              <Form.Item
                name="workEmail"
                label="Work email"
                tooltip="Typically @company.com — used for payslip + letter delivery"
                rules={[{ type: 'email', message: 'Enter a valid email' }, { max: 160 }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item name="workPhone" label="Work phone" rules={[{ max: 32 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="extension" label="Extension" rules={[{ max: 10 }]}>
                <Input placeholder="e.g. 4012" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="deskNumber" label="Desk / seat"
                tooltip="Facility + IT teams use this for asset assignment."
                rules={[{ max: 32 }]}>
                <Input placeholder="e.g. 4-A-12" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="gender" label="Gender" rules={[{ max: 16 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="birthDate" label="Date of birth">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="nationalId" label="National ID" rules={[{ max: 64 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="hireDate"
                label="Hire date"
                rules={[{ required: true, message: 'Hire date is required' }]}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            {/* M134 — Section 4 spec fields */}
            <Col span={8}>
              <Form.Item
                name="seniorityDate"
                label="Seniority date"
                tooltip="Tenure anchor for benefits + leave. Leave blank to use hire date. Rehires can carry forward their original date here."
                rules={[{
                  validator: (_, v: dayjs.Dayjs | undefined) =>
                    !v || !v.isAfter(dayjs(), 'day')
                      ? Promise.resolve()
                      : Promise.reject(new Error('Seniority date cannot be in the future')),
                }]}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="employeeCategory"
                label="Employee category"
                tooltip="Configurable bucket — e.g. white-collar / blue-collar, salaried / hourly, executive / IC."
                rules={[{ max: 60 }]}
              >
                <Input placeholder="Free text" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="departmentName" label="Department" rules={[{ max: 160 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="positionTitle" label="Position" rules={[{ max: 160 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="costCentre" label="Cost centre" rules={[{ max: 64 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          {/* M141 — work location */}
          <Form.Item
            name="workLocationId"
            label="Work location"
            tooltip="Primary physical site — drives geofencing, shift defaults, and location allowances."
          >
            <Select
              allowClear
              showSearch
              placeholder="— none —"
              optionFilterProp="label"
              options={locationOptions}
              style={{ maxWidth: 360 }}
            />
          </Form.Item>

          {/* M132 — remaining Section 1 cosmetic fields */}
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="placeOfBirth" label="Place of birth"
                rules={[{ max: 160 }]}>
                <Input placeholder="City, Country" />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="bloodGroup" label="Blood group">
                <Select allowClear placeholder="—"
                  options={BLOOD_GROUPS.map((g) => ({ value: g, label: g }))} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="nativeLanguage" label="Native language"
                tooltip="ISO 639-1 alpha-2 (lowercase). Drives letter-engine locale.">
                <Select allowClear showSearch placeholder="—"
                  optionFilterProp="label"
                  options={COMMON_LANGUAGES} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="religion" label="Religion (optional)"
                tooltip="Collection legally restricted in some jurisdictions."
                rules={[{ max: 60 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create employee'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      )}
    </FormPageShell>
  )
}
