import { useEffect, useMemo, useState, useCallback } from 'react'
import {
  Alert,
  Button,
  Checkbox,
  Col,
  DatePicker,
  Form,
  Input,
  Row,
  Select,
  Space,
  Typography,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate } from 'react-router-dom'
import { useTranslation, Trans } from 'react-i18next'
import { leaveApi, type LeaveBalance, type LeaveType, type LeaveSubmitRequest } from '../api/leave'
import { employeesApi, type Employee } from '../api/employees'
import { selfApi } from '../api/self'
import type { EmployeePeer } from '../api/self'
import { useAuth } from '../auth/AuthContext'
import { FormPageShell } from '../components/FormPageShell'
import { RoleSets } from '../auth/roleSets'

const LIST_PATH = '/leave/requests'

interface FormValues {
  employeeId: string
  leaveTypeId: string
  range: [dayjs.Dayjs, dayjs.Dayjs]
  halfDay?: boolean
  reason?: string
  replacementEmployeeId?: string
  attachmentUrl?: string
}

export function LeaveRequestFormPage() {
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  // M232 — all strings come from the leave namespace; the form
  // shell's Cancel button uses the shared common namespace.
  const { t } = useTranslation(['leave', 'common'])
  const [form] = Form.useForm<FormValues>()
  /** HR/admin can pick any employee; plain EMPLOYEE submits for themselves only. */
  const isHrMode = hasRole(...RoleSets.HR_PLUS_MANAGERS_READ)
  const [employees, setEmployees] = useState<Employee[]>([])
  const [peers, setPeers] = useState<EmployeePeer[]>([])
  const [selfLabel, setSelfLabel] = useState<string>('')
  const [types, setTypes] = useState<LeaveType[]>([])
  const [saving, setSaving] = useState(false)
  const [balance, setBalance] = useState<LeaveBalance | null>(null)

  useEffect(() => {
    if (isHrMode) {
      Promise.all([leaveApi.types(true), employeesApi.list({ size: 500 }), selfApi.peers()]).then(
        ([t, e, p]) => {
          setTypes(t.filter((x) => x.code !== 'PATERNITY'))
          setEmployees(e.content)
          setPeers(p)
        },
      )
    } else {
      Promise.all([leaveApi.types(true), selfApi.profile(), selfApi.peers()]).then(([t, p, pr]) => {
        setTypes(t.filter((x) => x.code !== 'PATERNITY'))
        setSelfLabel(`${p.firstName} ${p.lastName} (${p.employeeNo})`)
        form.setFieldsValue({ employeeId: p.id })
        setPeers(pr)
      })
    }
  }, [isHrMode, form])

  const disabledDate = useCallback(
    (d: dayjs.Dayjs) => d.isBefore(dayjs().startOf('day')),
    [],
  )

  const typeMap = useMemo(() => new Map(types.map((t) => [t.id, t])), [types])

  const refreshBalance = async (employeeId?: string, leaveTypeId?: string, year?: number) => {
    if (!employeeId || !leaveTypeId || !year) {
      setBalance(null)
      return
    }
    try {
      const list = await leaveApi.balances({ employeeId, year })
      setBalance(list.find((b) => b.leaveTypeId === leaveTypeId) ?? null)
    } catch {
      setBalance(null)
    }
  }

  const onValuesChange = (_: Partial<FormValues>, all: FormValues) => {
    const start = all.range?.[0]
    refreshBalance(all.employeeId, all.leaveTypeId, start?.year())
  }

  const onFinish = async (v: FormValues) => {
    const selectedType = typeMap.get(v.leaveTypeId)
    if (selectedType?.requiresAttachment && !v.attachmentUrl) {
      message.warning(t('leave:newRequest.messages.attachmentRequired', { code: selectedType.code }))
      return
    }
    setSaving(true)
    const payload: LeaveSubmitRequest = {
      employeeId: v.employeeId,
      leaveTypeId: v.leaveTypeId,
      startDate: v.range[0].format('YYYY-MM-DD'),
      endDate: v.range[1].format('YYYY-MM-DD'),
      halfDay: v.halfDay,
      reason: v.reason,
      replacementEmployeeId: v.replacementEmployeeId,
      attachmentUrl: v.attachmentUrl,
    }
    try {
      // EMPLOYEE role: use the self-service endpoint (forces employeeId to current user)
      // HR/admin: use the regular endpoint (employee picker already validated)
      if (isHrMode) {
        await leaveApi.submit(payload)
      } else {
        await selfApi.submitLeave(payload)
      }
      message.success(t('leave:newRequest.messages.submitted'))
      navigate(LIST_PATH)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          t('leave:newRequest.messages.submitFailed'),
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <FormPageShell title={t('leave:newRequest.title')} backTo={LIST_PATH}>
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
                label={t('leave:newRequest.employee')}
                rules={[{ required: true, message: t('leave:newRequest.validation.selectEmployee') }]}
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
                <Form.Item label={t('leave:newRequest.employee')}>
                  <Input disabled value={selfLabel} />
                </Form.Item>
                {/* Hidden form field holds the UUID for submission */}
                <Form.Item name="employeeId" hidden><Input /></Form.Item>
              </>
            )}
          </Col>
          <Col span={12}>
            <Form.Item
              name="leaveTypeId"
              label={t('leave:newRequest.leaveType')}
              rules={[{ required: true, message: t('leave:newRequest.validation.selectLeaveType') }]}
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
        <Form.Item
          name="range"
          label={t('leave:newRequest.dates')}
          rules={[{ required: true, message: t('leave:newRequest.validation.pickDates') }]}
        >
          <DatePicker.RangePicker
            disabledDate={disabledDate}
            style={{ width: '100%', maxWidth: 400 }}
          />
        </Form.Item>
        <Form.Item name="halfDay" valuePropName="checked">
          <Checkbox>{t('leave:newRequest.halfDay')}</Checkbox>
        </Form.Item>

        {balance && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message={
              <Space size="large">
                <span>{t('leave:newRequest.balance.entitlement')}: <strong>{balance.entitlementDays}</strong></span>
                <span>{t('leave:newRequest.balance.used')}: <strong>{balance.usedDays}</strong></span>
                <span>{t('leave:newRequest.balance.reserved')}: <strong>{balance.reservedDays}</strong></span>
                <span>{t('leave:newRequest.balance.remaining')}: <strong>{balance.remainingDays}</strong></span>
              </Space>
            }
          />
        )}

        <Form.Item name="reason" label={t('leave:newRequest.reason')}>
          <Input.TextArea rows={3} placeholder={t('leave:newRequest.reasonPlaceholder')} />
        </Form.Item>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="replacementEmployeeId"
              label={t('leave:newRequest.replacement')}
              tooltip={t('leave:newRequest.replacementTooltip')}
            >
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder={t('leave:newRequest.replacementPlaceholder')}
                options={peers.map((p) => ({
                  value: p.id,
                  label: `${p.employeeNo} — ${p.firstName} ${p.lastName}`,
                }))}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="attachmentUrl"
              label={t('leave:newRequest.attachmentUrl')}
              tooltip={t('leave:newRequest.attachmentTooltip')}
            >
              <Input placeholder="https://…/medical-certificate.pdf" />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item>
          <Space>
            <Button onClick={() => navigate(LIST_PATH)}>{t('common:cancel')}</Button>
            <Button type="primary" htmlType="submit" loading={saving}>
              {t('leave:newRequest.submit')}
            </Button>
          </Space>
        </Form.Item>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {/* <Trans> preserves the inline <code> markup across languages so
              both AZ and EN can use the same JSX structure with just the
              surrounding text translated. */}
          <Trans
            i18nKey="leave:newRequest.footer"
            components={{ code: <code /> }}
          />
        </Typography.Text>
      </Form>
    </FormPageShell>
  )
}
