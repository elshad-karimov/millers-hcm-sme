import { useState, useEffect, useCallback } from 'react'
import { Table, Button, Modal, Form, Select, InputNumber, Input, Switch, Space,
         Typography, Popconfirm, message } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  listNoticePeriodRules, createNoticePeriodRule, updateNoticePeriodRule,
  deleteNoticePeriodRule,
  type NoticePeriodRule, type NoticePeriodRuleRequest, type EmploymentType
} from '../api/offboarding'

const { Title } = Typography

const EMPLOYMENT_TYPES: { value: EmploymentType; label: string }[] = [
  { value: 'PERMANENT', label: 'Permanent' },
  { value: 'FIXED_TERM', label: 'Fixed Term' },
  { value: 'PART_TIME', label: 'Part Time' },
  { value: 'PROBATIONARY', label: 'Probationary' },
  { value: 'CONTRACTOR', label: 'Contractor' },
  { value: 'INTERN', label: 'Intern' },
]

export function NoticePeriodRulesPage() {
  const [rules, setRules] = useState<NoticePeriodRule[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [tick, setTick] = useState(0)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<NoticePeriodRule | null>(null)
  const [form] = Form.useForm()

  const refresh = useCallback(() => setTick(t => t + 1), [])

  useEffect(() => {
    setLoading(true)
    listNoticePeriodRules()
      .then(setRules)
      .catch(() => message.error('Failed to load notice period rules'))
      .finally(() => setLoading(false))
  }, [tick])

  function openCreate() {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  function openEdit(rule: NoticePeriodRule) {
    setEditing(rule)
    form.setFieldsValue({
      employmentType: rule.employmentType ?? null,
      onProbation: rule.onProbation,
      noticeDays: rule.noticeDays,
      description: rule.description ?? '',
    })
    setModalOpen(true)
  }

  async function handleSave() {
    let values: NoticePeriodRuleRequest
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    setSaving(true)
    try {
      if (editing) {
        await updateNoticePeriodRule(editing.id, values)
        message.success('Rule updated')
      } else {
        await createNoticePeriodRule(values)
        message.success('Rule created')
      }
      setModalOpen(false)
      refresh()
    } catch {
      message.error('Failed to save rule')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteNoticePeriodRule(id)
      message.success('Rule deactivated')
      refresh()
    } catch {
      message.error('Failed to deactivate rule')
    }
  }

  const columns: ColumnsType<NoticePeriodRule> = [
    {
      title: 'Employment Type',
      dataIndex: 'employmentType',
      render: (v?: string) => v ? EMPLOYMENT_TYPES.find(t => t.value === v)?.label ?? v : <em>All types (catch-all)</em>,
    },
    {
      title: 'On Probation',
      dataIndex: 'onProbation',
      render: (v: boolean) => v ? 'Yes' : 'No',
      width: 120,
    },
    {
      title: 'Notice Days',
      dataIndex: 'noticeDays',
      width: 120,
      render: (v: number) => `${v} days`,
    },
    {
      title: 'Description',
      dataIndex: 'description',
      render: (v?: string) => v || '—',
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)} />
          <Popconfirm
            title="Deactivate this rule?"
            onConfirm={() => handleDelete(record.id)}
            okText="Yes"
            cancelText="No"
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>Notice Period Rules</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Add Rule
        </Button>
      </div>

      <p style={{ color: '#666', marginBottom: 16 }}>
        Rules are matched in specificity order: probation + employment type &gt; employment type only &gt; catch-all (no type).
        Defaults to 30 days (AZ Labour Code art.70) if no rule matches.
      </p>

      <Table
        rowKey="id"
        loading={loading}
        dataSource={rules}
        columns={columns}
        pagination={false}
      />

      <Modal
        open={modalOpen}
        title={editing ? 'Edit Notice Period Rule' : 'New Notice Period Rule'}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        okText="Save"
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ onProbation: false, noticeDays: 30 }}>
          <Form.Item
            name="employmentType"
            label="Employment Type"
            extra="Leave blank to create a catch-all rule that applies to all employment types."
          >
            <Select allowClear placeholder="All types (catch-all)" options={EMPLOYMENT_TYPES} />
          </Form.Item>
          <Form.Item name="onProbation" label="Applies to employees on probation" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item
            name="noticeDays"
            label="Notice Days"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} addonAfter="days" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} placeholder="e.g. AZ Labour Code art.70 — standard 30-day notice" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
