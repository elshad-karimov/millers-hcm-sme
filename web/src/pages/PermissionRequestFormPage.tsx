import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  Row,
  Select,
  Space,
  TimePicker,
  Typography,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate } from 'react-router-dom'
import { useTranslation, Trans } from 'react-i18next'
import {
  permissionApi,
  type PermissionBalance,
  type PermissionSubmitRequest,
  type PermissionType,
} from '../api/permission'
import { employeesApi, type Employee } from '../api/employees'
import { selfApi } from '../api/self'
import { useAuth } from '../auth/AuthContext'
import { FormPageShell } from '../components/FormPageShell'
import { RoleSets } from '../auth/roleSets'

const LIST_PATH = '/permission/requests'

interface FormValues {
  employeeId: string
  permissionTypeId: string
  permissionDate: dayjs.Dayjs
  timeRange?: [dayjs.Dayjs, dayjs.Dayjs]
  reason?: string
  attachmentUrl?: string
}

export function PermissionRequestFormPage() {
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  // M234 — permission namespace + common for the shared Cancel button.
  const { t } = useTranslation(['permission', 'common'])
  const [form] = Form.useForm<FormValues>()
  const isHrMode = hasRole(...RoleSets.HR_PLUS_MANAGERS_READ)
  const [employees, setEmployees] = useState<Employee[]>([])
  const [selfLabel, setSelfLabel] = useState<string>('')
  const [types, setTypes] = useState<PermissionType[]>([])
  const [saving, setSaving] = useState(false)
  const [balance, setBalance] = useState<PermissionBalance | null>(null)

  useEffect(() => {
    if (isHrMode) {
      Promise.all([permissionApi.types(true), employeesApi.list({ size: 500 })]).then(([t, e]) => {
        setTypes(t)
        setEmployees(e.content)
      })
    } else {
      Promise.all([permissionApi.types(true), selfApi.profile()]).then(([t, p]) => {
        setTypes(t)
        setSelfLabel(`${p.firstName} ${p.lastName} (${p.employeeNo})`)
        form.setFieldsValue({ employeeId: p.id })
      })
    }
  }, [isHrMode, form])

  const typeMap = useMemo(() => new Map(types.map((t) => [t.id, t])), [types])

  const refreshBalance = async (employeeId?: string, permissionTypeId?: string, year?: number) => {
    if (!employeeId || !permissionTypeId || !year) {
      setBalance(null)
      return
    }
    try {
      const list = await permissionApi.balances({ employeeId, year })
      setBalance(list.find((b) => b.permissionTypeId === permissionTypeId) ?? null)
    } catch {
      setBalance(null)
    }
  }

  const onValuesChange = (_: Partial<FormValues>, all: FormValues) => {
    refreshBalance(all.employeeId, all.permissionTypeId, all.permissionDate?.year())
  }

  const onFinish = async (v: FormValues) => {
    const selectedType = typeMap.get(v.permissionTypeId)
    if (selectedType?.requiresAttachment && !v.attachmentUrl) {
      message.warning(t('permission:newRequest.messages.attachmentRequired', { code: selectedType.code }))
      return
    }
    setSaving(true)
    const payload: PermissionSubmitRequest = {
      employeeId: v.employeeId,
      permissionTypeId: v.permissionTypeId,
      permissionDate: v.permissionDate.format('YYYY-MM-DD'),
      startTime: v.timeRange?.[0]?.format('HH:mm:ss'),
      endTime: v.timeRange?.[1]?.format('HH:mm:ss'),
      reason: v.reason,
      attachmentUrl: v.attachmentUrl,
    }
    try {
      if (isHrMode) {
        await permissionApi.submit(payload)
      } else {
        await selfApi.submitPermission(payload)
      }
      message.success(t('permission:newRequest.messages.submitted'))
      navigate(LIST_PATH)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          t('permission:newRequest.messages.submitFailed'),
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <FormPageShell title={t('permission:newRequest.title')} backTo={LIST_PATH}>
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        onValuesChange={onValuesChange}
        style={{ maxWidth: 720 }}
      >
        <Row gutter={16}>
          <Col span={12}>
            {isHrMode ? (
              <Form.Item
                name="employeeId"
                label={t('permission:newRequest.employee')}
                rules={[{ required: true, message: t('permission:newRequest.validation.selectEmployee') }]}
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
                <Form.Item label={t('permission:newRequest.employee')}>
                  <Input disabled value={selfLabel} />
                </Form.Item>
                <Form.Item name="employeeId" hidden><Input /></Form.Item>
              </>
            )}
          </Col>
          <Col span={12}>
            <Form.Item
              name="permissionTypeId"
              label={t('permission:newRequest.permissionType')}
              rules={[{ required: true, message: t('permission:newRequest.validation.selectType') }]}
            >
              <Select
                options={types.map((t) => ({
                  value: t.id,
                  label: `${t.code} — ${t.name}`,
                }))}
              />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="permissionDate"
              label={t('permission:newRequest.date')}
              rules={[{ required: true, message: t('permission:newRequest.validation.pickDate') }]}
            >
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="timeRange"
              label={t('permission:newRequest.timeRange')}
              rules={[{ required: true, message: t('permission:newRequest.validation.pickTimeRange') }]}
            >
              <TimePicker.RangePicker format="HH:mm" minuteStep={15} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>

        {balance && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message={
              <Space size="large">
                <span>{t('permission:newRequest.balance.limit')}: <strong>{balance.limitHours}h</strong></span>
                <span>{t('permission:newRequest.balance.used')}: <strong>{balance.usedHours}h</strong></span>
                <span>{t('permission:newRequest.balance.reserved')}: <strong>{balance.reservedHours}h</strong></span>
                <span>{t('permission:newRequest.balance.remaining')}: <strong>{balance.remainingHours}h</strong></span>
              </Space>
            }
          />
        )}

        <Form.Item name="reason" label={t('permission:newRequest.reason')}>
          <Input.TextArea rows={3} />
        </Form.Item>
        <Form.Item name="attachmentUrl" label={t('permission:newRequest.attachmentUrl')}>
          <Input placeholder="https://…/doc.pdf" />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button onClick={() => navigate(LIST_PATH)}>{t('common:cancel')}</Button>
            <Button type="primary" htmlType="submit" loading={saving}>
              {t('permission:newRequest.submit')}
            </Button>
          </Space>
        </Form.Item>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          <Trans
            i18nKey="permission:newRequest.footer"
            components={{ code: <code /> }}
          />
        </Typography.Text>
      </Form>
    </FormPageShell>
  )
}
