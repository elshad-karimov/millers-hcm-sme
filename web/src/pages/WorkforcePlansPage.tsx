// M247 — Workforce plans list page.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import {
  SCENARIO_TYPE_COLOR,
  SCENARIO_TYPE_LABEL,
  workforcePlanApi,
  WORKFORCE_PLAN_STATUS_COLOR,
  WORKFORCE_PLAN_STATUS_LABEL,
  type ScenarioType,
  type WorkforcePlan,
  type WorkforcePlanHeaderRequest,
  type WorkforcePlanStatus,
} from '../api/workforcePlan'
import { legalEntitiesApi, type LegalEntityResponse } from '../api/legalEntities'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const SCENARIO_OPTIONS = (
  ['BASELINE', 'EXPANSION', 'REDUCTION', 'RESTRUCTURE', 'SEASONAL', 'WHAT_IF'] as ScenarioType[]
).map((t) => ({ value: t, label: SCENARIO_TYPE_LABEL[t] }))

export function WorkforcePlansPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<WorkforcePlan[]>([])
  const [entities, setEntities] = useState<LegalEntityResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [filterEntity, setFilterEntity] = useState<string | undefined>()
  const [newOpen, setNewOpen] = useState(false)

  type NewFormValues = {
    legalEntityId: string
    versionCode: string
    title?: string
    scenarioType: ScenarioType
    effectiveFrom: dayjs.Dayjs
    effectiveTo?: dayjs.Dayjs
    notes?: string
  }
  const [form] = Form.useForm<NewFormValues>()

  const load = () => {
    setLoading(true)
    workforcePlanApi.list(filterEntity)
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load plans'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    legalEntitiesApi.list().then(setEntities).catch(() => setEntities([]))
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterEntity])

  const entityLabel = (id: string) => {
    const e = entities.find((x) => x.id === id)
    return e ? `${e.name} (${e.code})` : id
  }

  const onCreate = async () => {
    const v = await form.validateFields()
    const body: WorkforcePlanHeaderRequest = {
      legalEntityId: v.legalEntityId,
      versionCode: v.versionCode,
      title: v.title,
      scenarioType: v.scenarioType,
      effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
      effectiveTo: v.effectiveTo ? v.effectiveTo.format('YYYY-MM-DD') : undefined,
      notes: v.notes,
    }
    try {
      const created = await workforcePlanApi.create(body)
      setNewOpen(false)
      form.resetFields()
      message.success('Workforce plan created')
      navigate(`/workforce-plans/${created.id}`)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not create')
    }
  }

  const cols: ColumnsType<WorkforcePlan> = [
    {
      title: 'Version',
      dataIndex: 'versionCode',
      width: 160,
      render: (v: string, r) => (
        <a onClick={() => navigate(`/workforce-plans/${r.id}`)}>
          <strong>{v}</strong>
        </a>
      ),
    },
    {
      title: 'Title',
      dataIndex: 'title',
      render: (v: string) => v ?? <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'Scenario',
      dataIndex: 'scenarioType',
      width: 120,
      render: (s: ScenarioType) => (
        <Tag color={SCENARIO_TYPE_COLOR[s]}>{SCENARIO_TYPE_LABEL[s]}</Tag>
      ),
    },
    {
      title: 'Legal entity',
      dataIndex: 'legalEntityId',
      render: (v: string) => entityLabel(v),
    },
    {
      title: 'Period',
      width: 200,
      render: (_, r) => (
        <span>
          {r.effectiveFrom} → {r.effectiveTo ?? <Typography.Text type="secondary">open</Typography.Text>}
        </span>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 140,
      render: (s: WorkforcePlanStatus) => (
        <Tag color={WORKFORCE_PLAN_STATUS_COLOR[s]}>
          {WORKFORCE_PLAN_STATUS_LABEL[s]}
        </Tag>
      ),
    },
    { title: 'Lines', dataIndex: 'totalLines', width: 70, align: 'right' as const },
    {
      title: 'Headcount',
      dataIndex: 'totalHeadcount',
      width: 100,
      align: 'right' as const,
    },
    {
      title: 'Monthly cost',
      width: 140,
      align: 'right' as const,
      render: (_, r) =>
        r.totalMonthlyCost != null
          ? Number(r.totalMonthlyCost).toLocaleString(undefined, {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })
          : '—',
    },
  ]

  return (
    <Card
      title="Workforce plans"
      extra={
        canWrite && (
          <Button type="primary" onClick={() => setNewOpen(true)}>
            + New plan
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 16 }}>
        <span>Legal entity:</span>
        <Select
          allowClear
          placeholder="All"
          style={{ width: 280 }}
          value={filterEntity}
          onChange={setFilterEntity}
          options={entities.map((e) => ({ value: e.id, label: `${e.name} (${e.code})` }))}
        />
      </Space>
      <Table
        size="small"
        rowKey="id"
        columns={cols}
        dataSource={rows}
        loading={loading}
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title="New workforce plan"
        open={newOpen}
        onOk={onCreate}
        onCancel={() => setNewOpen(false)}
        okText="Create"
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="legalEntityId" label="Legal entity" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={entities.map((e) => ({ value: e.id, label: `${e.name} (${e.code})` }))}
            />
          </Form.Item>
          <Form.Item
            name="versionCode"
            label="Version code"
            tooltip="Unique within the legal entity. e.g. 2026-baseline"
            rules={[{ required: true, max: 64 }]}
          >
            <Input placeholder="2026-baseline" />
          </Form.Item>
          <Form.Item name="title" label="Title">
            <Input placeholder="2026 Workforce Plan" maxLength={200} />
          </Form.Item>
          <Form.Item name="scenarioType" label="Scenario type" rules={[{ required: true }]}>
            <Select options={SCENARIO_OPTIONS} />
          </Form.Item>
          <Space size="small">
            <Form.Item name="effectiveFrom" label="Effective from" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
            <Form.Item name="effectiveTo" label="Effective to (optional)">
              <DatePicker />
            </Form.Item>
          </Space>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
