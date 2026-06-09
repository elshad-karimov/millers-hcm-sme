// M245 — Staffing table list page.
//
// One row per version. Shows status pill, period, totals, owner.
// Actions: create new, open detail, compare two versions.

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
  STAFFING_TABLE_STATUS_COLOR,
  STAFFING_TABLE_STATUS_LABEL,
  staffingTableApi,
  type StaffingTable,
  type StaffingTableHeaderRequest,
  type StaffingTableStatus,
} from '../api/staffingTable'
import { legalEntitiesApi, type LegalEntityResponse } from '../api/legalEntities'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

export function StaffingTablesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<StaffingTable[]>([])
  const [entities, setEntities] = useState<LegalEntityResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [filterEntity, setFilterEntity] = useState<string | undefined>()
  const [newOpen, setNewOpen] = useState(false)
  const [form] = Form.useForm<{
    legalEntityId: string
    versionCode: string
    title?: string
    effectiveFrom: dayjs.Dayjs
    effectiveTo?: dayjs.Dayjs
    notes?: string
  }>()

  const load = () => {
    setLoading(true)
    staffingTableApi
      .list(filterEntity)
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load staffing tables'),
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
    const body: StaffingTableHeaderRequest = {
      legalEntityId: v.legalEntityId,
      versionCode: v.versionCode,
      title: v.title,
      effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
      effectiveTo: v.effectiveTo ? v.effectiveTo.format('YYYY-MM-DD') : undefined,
      notes: v.notes,
    }
    try {
      const created = await staffingTableApi.create(body)
      setNewOpen(false)
      form.resetFields()
      message.success('Staffing table created')
      navigate(`/staffing-tables/${created.id}`)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not create')
    }
  }

  const cols: ColumnsType<StaffingTable> = [
    {
      title: 'Version',
      dataIndex: 'versionCode',
      width: 140,
      render: (v: string, r) => (
        <a onClick={() => navigate(`/staffing-tables/${r.id}`)}>
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
      title: 'Legal entity',
      dataIndex: 'legalEntityId',
      render: (v: string) => entityLabel(v),
    },
    {
      title: 'Period',
      width: 220,
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
      render: (s: StaffingTableStatus) => (
        <Tag color={STAFFING_TABLE_STATUS_COLOR[s]}>
          {STAFFING_TABLE_STATUS_LABEL[s]}
        </Tag>
      ),
    },
    {
      title: 'Lines',
      dataIndex: 'totalLines',
      align: 'right' as const,
      width: 70,
    },
    {
      title: 'Headcount',
      dataIndex: 'totalHeadcount',
      align: 'right' as const,
      width: 100,
    },
    {
      title: 'Monthly fund',
      align: 'right' as const,
      width: 140,
      render: (_, r) =>
        r.totalMonthlyFund != null
          ? Number(r.totalMonthlyFund).toLocaleString(undefined, {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })
          : '—',
    },
  ]

  return (
    <Card
      title="Staffing tables — Ştat cədvəli"
      extra={
        canWrite && (
          <Button type="primary" onClick={() => setNewOpen(true)}>
            + New staffing table
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
        title="New staffing table"
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
            tooltip="Unique within the legal entity. e.g. 2026-Q1"
            rules={[{ required: true, max: 64 }]}
          >
            <Input placeholder="2026-Q1" />
          </Form.Item>
          <Form.Item name="title" label="Title">
            <Input placeholder="Ştat cədvəli 2026 Q1" maxLength={200} />
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
