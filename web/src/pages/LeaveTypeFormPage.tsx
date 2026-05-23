import { useEffect, useState } from 'react'
import {
  Button,
  Checkbox,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Space,
  Spin,
  App as AntdApp,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import { leaveApi, type LeaveType, type LeaveTypeRequest } from '../api/leave'
import { FormPageShell } from '../components/FormPageShell'

const LIST_PATH = '/leave/types'

interface FormValues {
  code: string
  name: string
  description?: string
  paid?: boolean
  requiresAttachment?: boolean
  requiresReplacement?: boolean
  defaultAnnualEntitlementDays?: number
  carryForwardLimitDays?: number
  maxConsecutiveDays?: number
  excludeWeekends?: boolean
  excludeHolidays?: boolean
  active?: boolean
}

export function LeaveTypeFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)

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
          maxConsecutiveDays: t.maxConsecutiveDays ?? undefined,
          excludeWeekends: t.excludeWeekends,
          excludeHolidays: t.excludeHolidays,
          active: t.active,
        })
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load leave type'),
      )
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const payload: LeaveTypeRequest = { ...v }
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
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 720 }}>
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
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="defaultAnnualEntitlementDays"
                label="Default annual entitlement (days)"
                tooltip="Leave blank for no entitlement bank (e.g. sick / educational)"
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
          <Form.Item>
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
