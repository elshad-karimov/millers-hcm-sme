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
import { permissionApi, type PermissionType, type PermissionTypeRequest } from '../api/permission'
import { FormPageShell } from '../components/FormPageShell'

const LIST_PATH = '/permission/types'

interface FormValues {
  code: string
  name: string
  description?: string
  annualLimitHours?: number
  paid?: boolean
  requiresAttachment?: boolean
  active?: boolean
}

export function PermissionTypeFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!editing) {
      form.setFieldsValue({ paid: true, active: true })
      return
    }
    setLoading(true)
    permissionApi
      .getType(id!)
      .then((t: PermissionType) => {
        form.setFieldsValue({
          code: t.code,
          name: t.name,
          description: t.description ?? undefined,
          annualLimitHours: t.annualLimitHours ?? undefined,
          paid: t.paid,
          requiresAttachment: t.requiresAttachment,
          active: t.active,
        })
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load permission type'),
      )
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const payload: PermissionTypeRequest = { ...v }
    try {
      if (editing) {
        await permissionApi.updateType(id!, payload)
        message.success('Permission type updated')
      } else {
        await permissionApi.createType(payload)
        message.success('Permission type created')
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
      title={editing ? 'Edit permission type' : 'New permission type'}
      backTo={LIST_PATH}
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 640 }}>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 64 }]}>
                <Input placeholder="PERSONAL" />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="name" label="Name" rules={[{ required: true, max: 200 }]}>
                <Input placeholder="Personal permission" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item
            name="annualLimitHours"
            label="Annual limit (hours)"
            tooltip="Leave blank for no annual cap"
          >
            <InputNumber min={0} step={0.5} style={{ width: 200 }} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="paid" valuePropName="checked">
                <Checkbox>Paid</Checkbox>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="requiresAttachment" valuePropName="checked">
                <Checkbox>Requires attachment</Checkbox>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="active" valuePropName="checked">
                <Checkbox>Active</Checkbox>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create permission type'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      )}
    </FormPageShell>
  )
}
