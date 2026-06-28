import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Checkbox,
  Col,
  Divider,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Spin,
  Tooltip,
  Typography,
  App as AntdApp,
} from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import {
  leaveApi,
  type LeaveType,
  type LeaveTypeRequest,
  type LeaveUnit,
  type SeniorityBracket,
} from '../api/leave'
import { FormPageShell } from '../components/FormPageShell'

const { Text } = Typography

const LIST_PATH = '/leave/types'

interface BracketRow {
  yearsMin: number
  yearsMax?: number | null
  annualDays: number
}

interface FormValues {
  code: string
  name: string
  description?: string
  paid?: boolean
  requiresAttachment?: boolean
  requiresReplacement?: boolean
  defaultAnnualEntitlementDays?: number
  carryForwardLimitDays?: number
  carryForwardExpiryMonths?: number
  maxConsecutiveDays?: number
  excludeWeekends?: boolean
  excludeHolidays?: boolean
  active?: boolean
  accruesMonthly?: boolean
  monthlyAccrualDays?: number
  negativeBalanceAllowed?: boolean
  maxNegativeDays?: number
  /** M341 */
  leaveUnit?: LeaveUnit
  hoursPerDay?: number
}

export function LeaveTypeFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)

  // Seniority brackets managed as separate state (dynamic rows)
  const [brackets, setBrackets] = useState<BracketRow[]>([])
  const [bracketError, setBracketError] = useState<string | null>(null)

  useEffect(() => {
    if (!editing) {
      form.setFieldsValue({ paid: true, active: true, excludeWeekends: false })
      return
    }
    setLoading(true)
    leaveApi
      .getType(id!)
      .then((t: LeaveType) => {
        form.setFieldsValue({
          code: t.code,
          name: t.name,
          description: t.description ?? undefined,
          paid: t.paid,
          requiresAttachment: t.requiresAttachment,
          requiresReplacement: t.requiresReplacement,
          defaultAnnualEntitlementDays: t.defaultAnnualEntitlementDays ?? undefined,
          carryForwardLimitDays: t.carryForwardLimitDays ?? undefined,
          carryForwardExpiryMonths: t.carryForwardExpiryMonths ?? undefined,
          maxConsecutiveDays: t.maxConsecutiveDays ?? undefined,
          excludeWeekends: t.excludeWeekends,
          excludeHolidays: t.excludeHolidays,
          active: t.active,
          accruesMonthly: t.accruesMonthly,
          monthlyAccrualDays: t.monthlyAccrualDays ?? undefined,
          negativeBalanceAllowed: t.negativeBalanceAllowed,
          maxNegativeDays: t.maxNegativeDays ?? undefined,
          leaveUnit: t.leaveUnit ?? 'DAYS',
          hoursPerDay: t.hoursPerDay ?? undefined,
        })
        if (t.seniorityBrackets && t.seniorityBrackets.length > 0) {
          setBrackets(
            t.seniorityBrackets.map((b) => ({
              yearsMin: b.yearsMin,
              yearsMax: b.yearsMax ?? null,
              annualDays: Number(b.annualDays),
            })),
          )
        }
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load leave type'),
      )
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  // ─── Bracket helpers ───────────────────────────────────────────────────────

  const addBracket = () => {
    const nextMin = brackets.length > 0 ? (brackets[brackets.length - 1].yearsMin + 5) : 0
    setBrackets([...brackets, { yearsMin: nextMin, yearsMax: null, annualDays: 21 }])
    setBracketError(null)
  }

  const removeBracket = (idx: number) => {
    setBrackets(brackets.filter((_, i) => i !== idx))
    setBracketError(null)
  }

  const updateBracket = (idx: number, field: keyof BracketRow, value: number | null | undefined) => {
    setBrackets(
      brackets.map((b, i) =>
        i === idx ? { ...b, [field]: value ?? (field === 'yearsMax' ? null : 0) } : b,
      ),
    )
    setBracketError(null)
  }

  const validateBrackets = (): SeniorityBracket[] | null => {
    if (brackets.length === 0) return null
    for (const b of brackets) {
      if (b.annualDays <= 0) {
        setBracketError('Annual days must be > 0 in every bracket')
        return null
      }
      if (b.yearsMin < 0) {
        setBracketError('Years min cannot be negative')
        return null
      }
      if (b.yearsMax != null && b.yearsMax < b.yearsMin) {
        setBracketError('Years max must be ≥ years min in every bracket')
        return null
      }
    }
    const mins = brackets.map((b) => b.yearsMin)
    if (new Set(mins).size !== mins.length) {
      setBracketError('Duplicate "years min" values are not allowed')
      return null
    }
    setBracketError(null)
    return brackets.map((b) => ({
      yearsMin: b.yearsMin,
      yearsMax: b.yearsMax ?? undefined,
      annualDays: b.annualDays,
    }))
  }

  // ─── Submit ────────────────────────────────────────────────────────────────

  const onFinish = async (v: FormValues) => {
    const validatedBrackets = validateBrackets()
    // validateBrackets returns null both for "no brackets" (ok) and "error"
    if (brackets.length > 0 && validatedBrackets === null) return // error state set above

    setSaving(true)
    const payload: LeaveTypeRequest = {
      ...v,
      seniorityBrackets: validatedBrackets ?? undefined,
    }
    try {
      if (editing) {
        await leaveApi.updateType(id!, payload)
        message.success('Leave type updated')
      } else {
        await leaveApi.createType(payload)
        message.success('Leave type created')
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
    <FormPageShell
      title={editing ? 'Edit leave type' : 'New leave type'}
      backTo={LIST_PATH}
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 760 }}>
          {/* ── Identity ─────────────────────────────────────────────── */}
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 64 }]}>
                <Input placeholder="ANNUAL" />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="name" label="Name" rules={[{ required: true, max: 200 }]}>
                <Input placeholder="Annual leave" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>

          {/* ── Entitlement ───────────────────────────────────────────── */}
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="defaultAnnualEntitlementDays"
                label="Default annual entitlement (days)"
                tooltip="Used as the fallback when no seniority brackets are configured"
              >
                <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="carryForwardLimitDays"
                label="Carry-forward limit (days)"
                tooltip="Leave blank for unlimited; 0 = no carry-forward"
              >
                <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="maxConsecutiveDays" label="Max consecutive days">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="carryForwardExpiryMonths"
                label="Carry-forward expiry (months)"
                tooltip="Carried-forward days expire this many months into the new year. Leave blank = no expiry."
              >
                <InputNumber min={1} max={12} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          {/* ── Flags ─────────────────────────────────────────────────── */}
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="paid" valuePropName="checked">
                <Checkbox>Paid</Checkbox>
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="requiresAttachment" valuePropName="checked">
                <Checkbox>Requires attachment</Checkbox>
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="requiresReplacement" valuePropName="checked">
                <Checkbox>Requires replacement</Checkbox>
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="active" valuePropName="checked">
                <Checkbox>Active</Checkbox>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="excludeWeekends" valuePropName="checked">
                <Checkbox>Skip weekends from day count</Checkbox>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="excludeHolidays" valuePropName="checked">
                <Checkbox>Skip public holidays from day count</Checkbox>
              </Form.Item>
            </Col>
          </Row>

          {/* ── Monthly accrual ───────────────────────────────────────── */}
          <Divider orientation="left" style={{ fontSize: 13 }}>
            Monthly accrual (PRD 8.5.2)
          </Divider>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="accruesMonthly" valuePropName="checked">
                <Checkbox>Accrues monthly</Checkbox>
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item
                name="monthlyAccrualDays"
                label="Explicit monthly bump (days)"
                tooltip="Leave blank to derive from default annual ÷ 12, or from seniority brackets"
              >
                <InputNumber min={0} step={0.25} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          {/* ── Negative balance ──────────────────────────────────────── */}
          <Divider orientation="left" style={{ fontSize: 13 }}>
            Negative balance (M340)
          </Divider>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="negativeBalanceAllowed" valuePropName="checked">
                <Checkbox>Allow negative balance</Checkbox>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="maxNegativeDays"
                label="Max negative days"
                tooltip="Maximum deficit allowed (e.g. 5 = employee can go 5 days below zero). Leave blank for unlimited negative."
              >
                <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          {/* ── Leave unit (M341) ─────────────────────────────────────── */}
          <Divider orientation="left" style={{ fontSize: 13 }}>
            Request unit (M341)
          </Divider>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="leaveUnit"
                label="Leave unit"
                tooltip="DAYS = whole/half-day; HALF_DAY = type forces half-day only; HOURS = time-based with clock times"
                initialValue="DAYS"
              >
                <Select>
                  <Select.Option value="DAYS">Days</Select.Option>
                  <Select.Option value="HALF_DAY">Half-day only</Select.Option>
                  <Select.Option value="HOURS">Hours (time-based)</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="hoursPerDay"
                label="Working hours per day"
                tooltip="Used to convert requested hours → fractional leave days (HOURS unit only)"
              >
                <InputNumber min={1} max={24} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          {/* ── Seniority brackets ────────────────────────────────────── */}
          <Divider orientation="left" style={{ fontSize: 13 }}>
            Seniority brackets (M47)
          </Divider>
          <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: 12 }}>
            Optional. When brackets are defined they override the monthly bump above.
            The accrual walker matches each employee's completed years of service on the
            1st of the target month to the first matching bracket and uses{' '}
            <em>annualDays ÷ 12</em> as their monthly credit.
          </Text>

          {bracketError && (
            <Alert
              type="error"
              message={bracketError}
              showIcon
              style={{ marginBottom: 12 }}
            />
          )}

          {brackets.length > 0 && (
            <div style={{ marginBottom: 8 }}>
              {/* Column headings */}
              <Row gutter={8} style={{ marginBottom: 4 }}>
                <Col span={5}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Years min (incl.)
                  </Text>
                </Col>
                <Col span={5}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Years max (incl., blank = ∞)
                  </Text>
                </Col>
                <Col span={6}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Annual days
                  </Text>
                </Col>
                <Col span={4}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Monthly equiv.
                  </Text>
                </Col>
              </Row>

              {brackets.map((b, idx) => (
                <Row gutter={8} key={idx} style={{ marginBottom: 6, alignItems: 'center' }}>
                  <Col span={5}>
                    <InputNumber
                      min={0}
                      value={b.yearsMin}
                      onChange={(v) => updateBracket(idx, 'yearsMin', v ?? 0)}
                      style={{ width: '100%' }}
                    />
                  </Col>
                  <Col span={5}>
                    <Tooltip title="Leave blank for 'and above'">
                      <InputNumber
                        min={0}
                        value={b.yearsMax ?? undefined}
                        placeholder="∞"
                        onChange={(v) => updateBracket(idx, 'yearsMax', v ?? null)}
                        style={{ width: '100%' }}
                      />
                    </Tooltip>
                  </Col>
                  <Col span={6}>
                    <InputNumber
                      min={0.1}
                      step={0.5}
                      value={b.annualDays}
                      onChange={(v) => updateBracket(idx, 'annualDays', v ?? 0)}
                      style={{ width: '100%' }}
                    />
                  </Col>
                  <Col span={4}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {b.annualDays > 0
                        ? `${(b.annualDays / 12).toFixed(2)} d/mo`
                        : '—'}
                    </Text>
                  </Col>
                  <Col span={4}>
                    <Button
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={() => removeBracket(idx)}
                      size="small"
                    />
                  </Col>
                </Row>
              ))}
            </div>
          )}

          <Button
            type="dashed"
            icon={<PlusOutlined />}
            onClick={addBracket}
            style={{ marginBottom: 16 }}
          >
            Add seniority bracket
          </Button>

          {/* ── Actions ───────────────────────────────────────────────── */}
          <Form.Item style={{ marginTop: 8 }}>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create leave type'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      )}
    </FormPageShell>
  )
}
