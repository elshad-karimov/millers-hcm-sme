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
  // M132 — Section 1 cosmetic fields
  preferredName?: string
  placeOfBirth?: string
  bloodGroup?: string
  religion?: string
  nativeLanguage?: string
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
          // M132 — Section 1 cosmetic fields
          preferredName: e.preferredName ?? undefined,
          placeOfBirth: e.placeOfBirth ?? undefined,
          bloodGroup: e.bloodGroup ?? undefined,
          religion: e.religion ?? undefined,
          nativeLanguage: e.nativeLanguage ?? undefined,
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
            <Col span={12}>
              <Form.Item
                name="email"
                label="Email"
                rules={[{ type: 'email', message: 'Enter a valid email' }, { max: 160 }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label="Phone" rules={[{ max: 32 }]}>
                <Input />
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
          <Form.Item
            name="hireDate"
            label="Hire date"
            rules={[{ required: true, message: 'Hire date is required' }]}
          >
            <DatePicker style={{ width: '100%', maxWidth: 240 }} />
          </Form.Item>
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
