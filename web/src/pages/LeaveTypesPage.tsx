import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import {
  leaveApi,
  type LeaveType,
  type LeaveEntitlementRule,
  type LeaveEntitlementRuleRequest,
  EMPLOYMENT_TYPES,
} from '../api/leave'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

export function LeaveTypesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<LeaveType[]>([])
  const [loading, setLoading] = useState(false)

  // Entitlement rules modal state
  const [rulesTypeId, setRulesTypeId] = useState<string | null>(null)
  const [rulesTypeName, setRulesTypeName] = useState<string>('')
  const [ruleRows, setRuleRows] = useState<LeaveEntitlementRule[]>([])
  const [rulesLoading, setRulesLoading] = useState(false)
  const [ruleForm] = Form.useForm<LeaveEntitlementRuleRequest>()
  const [editingRule, setEditingRule] = useState<LeaveEntitlementRule | null>(null)
  const [ruleFormOpen, setRuleFormOpen] = useState(false)
  const [ruleSaving, setRuleSaving] = useState(false)

  const openRulesModal = (type: LeaveType) => {
    setRulesTypeId(type.id)
    setRulesTypeName(type.name)
    setRulesLoading(true)
    leaveApi
      .entitlementRules(type.id)
      .then(setRuleRows)
      .catch(() => message.error('Failed to load entitlement rules'))
      .finally(() => setRulesLoading(false))
  }

  const loadRules = () => {
    if (!rulesTypeId) return
    setRulesLoading(true)
    leaveApi
      .entitlementRules(rulesTypeId)
      .then(setRuleRows)
      .catch(() => message.error('Failed to load entitlement rules'))
      .finally(() => setRulesLoading(false))
  }

  const openAddRule = () => {
    setEditingRule(null)
    ruleForm.resetFields()
    ruleForm.setFieldsValue({ priority: 0, active: true })
    setRuleFormOpen(true)
  }

  const openEditRule = (rule: LeaveEntitlementRule) => {
    setEditingRule(rule)
    ruleForm.setFieldsValue({
      employmentType: rule.employmentType ?? undefined,
      minTenureMonths: rule.minTenureMonths ?? undefined,
      maxTenureMonths: rule.maxTenureMonths ?? undefined,
      annualEntitlementDays: rule.annualEntitlementDays,
      priority: rule.priority,
      active: rule.active,
    })
    setRuleFormOpen(true)
  }

  const saveRule = () => {
    if (!rulesTypeId) return
    ruleForm.validateFields().then((values) => {
      setRuleSaving(true)
      const promise = editingRule
        ? leaveApi.updateEntitlementRule(rulesTypeId, editingRule.id, values)
        : leaveApi.createEntitlementRule(rulesTypeId, values)
      promise
        .then(() => {
          message.success(editingRule ? 'Rule updated' : 'Rule created')
          setRuleFormOpen(false)
          loadRules()
        })
        .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to save rule'))
        .finally(() => setRuleSaving(false))
    })
  }

  const toggleRule = (rule: LeaveEntitlementRule) => {
    if (!rulesTypeId) return
    leaveApi
      .toggleEntitlementRule(rulesTypeId, rule.id)
      .then(() => {
        message.success(rule.active ? 'Rule deactivated' : 'Rule activated')
        loadRules()
      })
      .catch(() => message.error('Failed to toggle rule'))
  }

  const ruleColumns: ColumnsType<LeaveEntitlementRule> = [
    {
      title: 'Employment type',
      dataIndex: 'employmentType',
      render: (v) => v ?? <Tag color="default">Any</Tag>,
    },
    {
      title: 'Tenure window (months)',
      render: (_, r) => {
        const min = r.minTenureMonths != null ? `${r.minTenureMonths}` : '0'
        const max = r.maxTenureMonths != null ? `${r.maxTenureMonths}` : '∞'
        return `${min} – ${max}`
      },
    },
    {
      title: 'Annual entitlement',
      dataIndex: 'annualEntitlementDays',
      render: (v: number) => `${v} days`,
    },
    {
      title: 'Monthly',
      dataIndex: 'annualEntitlementDays',
      render: (v: number) => `${(v / 12).toFixed(2)} d/mo`,
    },
    { title: 'Priority', dataIndex: 'priority', width: 80 },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Yes' : 'No'}</Tag>,
    },
    canEdit
      ? {
          title: '',
          width: 140,
          render: (_, r) => (
            <Space>
              <Button size="small" onClick={() => openEditRule(r)}>Edit</Button>
              <Button size="small" onClick={() => toggleRule(r)}>
                {r.active ? 'Disable' : 'Enable'}
              </Button>
            </Space>
          ),
        }
      : { title: '', width: 0, render: () => null },
  ]

  const load = () => {
    setLoading(true)
    leaveApi
      .types()
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load leave types'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const columns: ColumnsType<LeaveType> = [
    { title: 'Code', dataIndex: 'code', width: 140 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Paid',
      dataIndex: 'paid',
      width: 70,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Paid' : 'Unpaid'}</Tag>,
    },
    {
      title: 'Annual entitlement',
      dataIndex: 'defaultAnnualEntitlementDays',
      width: 160,
      render: (v?: number | null) =>
        v === null || v === undefined ? '—' : `${v} day${v === 1 ? '' : 's'}`,
    },
    {
      title: 'Carry-forward limit',
      dataIndex: 'carryForwardLimitDays',
      width: 160,
      render: (v?: number | null) =>
        v === null || v === undefined ? 'Unlimited' : `${v} day${v === 1 ? '' : 's'}`,
    },
    {
      title: 'Accrual',
      width: 180,
      render: (_, r) => {
        if (!r.accruesMonthly) return <Tag color="default">one-shot</Tag>
        if (r.seniorityBrackets && r.seniorityBrackets.length > 0) {
          return (
            <Tag color="purple">
              {r.seniorityBrackets.length} seniority tier{r.seniorityBrackets.length > 1 ? 's' : ''}
            </Tag>
          )
        }
        const flat = r.monthlyAccrualDays ?? (r.defaultAnnualEntitlementDays != null ? +(r.defaultAnnualEntitlementDays / 12).toFixed(2) : null)
        return flat != null ? <Tag color="blue">{flat} d/mo</Tag> : <Tag color="blue">monthly</Tag>
      },
    },
    {
      title: 'Flags',
      render: (_, r) => (
        <Space size={[4, 4]} wrap>
          {r.requiresAttachment && <Tag>attachment</Tag>}
          {r.requiresReplacement && <Tag>replacement</Tag>}
          {r.excludeWeekends && <Tag>skip weekends</Tag>}
          {r.excludeHolidays && <Tag>skip holidays</Tag>}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) => (
        <Tag color={v ? 'green' : 'default'}>{v ? 'Active' : 'Disabled'}</Tag>
      ),
    },
    {
      title: '',
      width: canEdit ? 180 : 100,
      render: (_, r) => (
        <Space>
          <Button size="small" onClick={() => openRulesModal(r)}>Rules</Button>
          {canEdit && (
            <Button size="small" onClick={() => navigate(`/leave/types/${r.id}/edit`)}>Edit</Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <Card
        title={<Typography.Title level={4} style={{ margin: 0 }}>Leave types</Typography.Title>}
        extra={
          canEdit && (
            <Button type="primary" onClick={() => navigate('/leave/types/new')}>
              New leave type
            </Button>
          )
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={false}
        />
      </Card>

      {/* Entitlement rules modal */}
      <Modal
        open={!!rulesTypeId}
        title={`Entitlement rules — ${rulesTypeName}`}
        width={800}
        onCancel={() => { setRulesTypeId(null); setRuleRows([]) }}
        footer={
          canEdit ? (
            <Button type="primary" onClick={openAddRule}>Add rule</Button>
          ) : null
        }
      >
        <Table
          rowKey="id"
          columns={ruleColumns}
          dataSource={ruleRows}
          loading={rulesLoading}
          pagination={false}
          size="small"
        />
      </Modal>

      {/* Add / edit rule modal */}
      <Modal
        open={ruleFormOpen}
        title={editingRule ? 'Edit entitlement rule' : 'New entitlement rule'}
        onCancel={() => setRuleFormOpen(false)}
        onOk={saveRule}
        confirmLoading={ruleSaving}
        okText={editingRule ? 'Save' : 'Create'}
        destroyOnClose
      >
        <Form form={ruleForm} layout="vertical">
          <Form.Item
            label="Employment type (leave blank to match any)"
            name="employmentType"
          >
            <Select allowClear placeholder="Any employment type">
              {EMPLOYMENT_TYPES.map((t) => (
                <Select.Option key={t} value={t}>{t}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item label="Min tenure months (0 = from hire)" name="minTenureMonths">
            <InputNumber min={0} style={{ width: '100%' }} placeholder="Leave blank for no lower bound" />
          </Form.Item>
          <Form.Item label="Max tenure months" name="maxTenureMonths">
            <InputNumber min={0} style={{ width: '100%' }} placeholder="Leave blank for no upper bound" />
          </Form.Item>
          <Form.Item
            label="Annual entitlement days"
            name="annualEntitlementDays"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Priority (higher wins)" name="priority">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Active" name="active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
