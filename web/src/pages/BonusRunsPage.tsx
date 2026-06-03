import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  compBenefitsApi,
  type BonusItemSource,
  type BonusRun,
  type BonusRunGenerateRequest,
  type BonusRunItem,
  type BonusRunStatus,
} from '../api/compbenefits'
import { performanceApi, type ReviewCycle } from '../api/performance'
import { payrollApi, type PayrollRun } from '../api/payroll'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const STATUS_COLOR: Record<BonusRunStatus, string> = {
  DRAFT: 'default',
  GENERATED: 'gold',
  PUSHED: 'green',
  CANCELLED: 'red',
}

const SOURCE_COLOR: Record<BonusItemSource, string> = {
  MATRIX_LOOKUP: 'blue',
  REVIEW_OVERRIDE: 'purple',
  MANUAL: 'orange',
}

interface GenerateForm {
  name: string
  cycleId: string
  period: dayjs.Dayjs
  currency: string
  note?: string
}

interface PushForm {
  targetPayrollRunId: string
}

export function BonusRunsPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<BonusRun[]>([])
  const [loading, setLoading] = useState(false)
  const [selected, setSelected] = useState<BonusRun | null>(null)
  const [items, setItems] = useState<BonusRunItem[]>([])
  const [itemsLoading, setItemsLoading] = useState(false)

  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [payrollRuns, setPayrollRuns] = useState<PayrollRun[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])

  const [genOpen, setGenOpen] = useState(false)
  const [genForm] = Form.useForm<GenerateForm>()
  const [pushOpen, setPushOpen] = useState(false)
  const [pushForm] = Form.useForm<PushForm>()

  const load = () => {
    setLoading(true)
    compBenefitsApi
      .bonusRuns({ size: 100 })
      .then((p) => setRows(p.content))
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load bonus runs'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    Promise.all([
      performanceApi.cycles(),
      payrollApi.runs(),
      employeesApi.list({ size: 500 }).then((p) => p.content),
    ]).then(([c, pr, e]) => {
      setCycles(c)
      setPayrollRuns(pr)
      setEmployees(e)
    })
  }, [])

  const empMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])
  const payrollMap = useMemo(() => new Map(payrollRuns.map((p) => [p.id, p])), [payrollRuns])

  const loadItems = async (runId: string) => {
    setItemsLoading(true)
    try {
      const it = await compBenefitsApi.bonusRunItems(runId)
      setItems(it)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to load items',
      )
    } finally {
      setItemsLoading(false)
    }
  }

  const onSelect = async (r: BonusRun) => {
    setSelected(r)
    await loadItems(r.id)
  }

  const onGenerate = async (v: GenerateForm) => {
    const payload: BonusRunGenerateRequest = {
      name: v.name,
      cycleId: v.cycleId,
      periodYear: v.period.year(),
      periodMonth: v.period.month() + 1,
      currency: v.currency,
      note: v.note,
    }
    try {
      const run = await compBenefitsApi.generateBonusRun(payload)
      message.success(`Bonus run ${run.runNo} generated — ${run.employeeCount} items, total ${run.totalAmount} ${run.currency}`)
      setGenOpen(false)
      genForm.resetFields()
      load()
      await onSelect(run)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Generate failed',
      )
    }
  }

  const onPush = async (v: PushForm) => {
    if (!selected) return
    try {
      await compBenefitsApi.pushBonusRun(selected.id, v.targetPayrollRunId)
      message.success('Pushed to payroll')
      setPushOpen(false)
      pushForm.resetFields()
      load()
      const updated = await compBenefitsApi.bonusRun(selected.id)
      setSelected(updated)
      await loadItems(updated.id)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Push failed',
      )
    }
  }

  const onCancel = () => {
    if (!selected) return
    modal.confirm({
      title: `Cancel ${selected.runNo}?`,
      onOk: async () => {
        try {
          await compBenefitsApi.cancelBonusRun(selected.id, 'admin cancel')
          message.success('Cancelled')
          load()
          const updated = await compBenefitsApi.bonusRun(selected.id)
          setSelected(updated)
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Cancel failed',
          )
        }
      },
    })
  }

  const runColumns: ColumnsType<BonusRun> = [
    { title: 'Run #', dataIndex: 'runNo', width: 120 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Period',
      render: (_, r) => `${String(r.periodYear)}-${String(r.periodMonth).padStart(2, '0')}`,
      width: 100,
    },
    { title: 'Employees', dataIndex: 'employeeCount', width: 100, align: 'right' },
    {
      title: 'Total',
      render: (_, r) => `${r.totalAmount} ${r.currency}`,
      width: 150,
      align: 'right',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: BonusRunStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <Button size="small" onClick={() => onSelect(r)}>
          Open
        </Button>
      ),
    },
  ]

  const itemColumns: ColumnsType<BonusRunItem> = [
    { title: 'Item #', dataIndex: 'itemNo', width: 120 },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = empMap.get(id)
        return e ? `${e.employeeNo} ${e.lastName} ${e.firstName}` : id
      },
    },
    {
      title: 'Recommendation',
      dataIndex: 'recommendation',
      width: 160,
      render: (v: string | null) => (v ? <Tag color="purple">{v}</Tag> : '—'),
    },
    {
      title: 'Rating',
      dataIndex: 'finalRating',
      width: 90,
      align: 'right',
      render: (v: number | null) => v ?? '—',
    },
    {
      title: 'Base',
      dataIndex: 'baseSalary',
      width: 120,
      align: 'right',
      render: (v: number | null) => v ?? '—',
    },
    {
      title: 'Bonus %',
      dataIndex: 'bonusPercent',
      width: 90,
      align: 'right',
      render: (v: number | null) => (v != null ? `${v}%` : '—'),
    },
    {
      title: 'Amount',
      render: (_, r) => (
        <Typography.Text strong>{`${r.bonusAmount} ${r.currency}`}</Typography.Text>
      ),
      width: 140,
      align: 'right',
    },
    {
      title: 'Source',
      dataIndex: 'source',
      width: 150,
      render: (s: BonusItemSource) => <Tag color={SOURCE_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Pushed',
      dataIndex: 'pushedPayrollBonusId',
      width: 100,
      render: (v: string | null) => (v ? <Tag color="green">YES</Tag> : '—'),
    },
    {
      title: 'Note',
      dataIndex: 'note',
      ellipsis: true,
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title={
          <Typography.Title level={4} style={{ margin: 0 }}>
            Bonus runs
          </Typography.Title>
        }
        extra={
          canEdit && (
            <Button type="primary" onClick={() => setGenOpen(true)}>
              Generate from cycle
            </Button>
          )
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={runColumns}
          dataSource={rows}
          pagination={false}
        />
      </Card>

      {selected && (
        <Card
          title={`${selected.runNo} — ${selected.name}`}
          extra={
            <Space>
              <Tag color={STATUS_COLOR[selected.status]} style={{ fontSize: 13 }}>
                {selected.status}
              </Tag>
              {canEdit && selected.status === 'GENERATED' && (
                <>
                  <Button type="primary" onClick={() => setPushOpen(true)}>
                    Push to payroll
                  </Button>
                  <Button danger onClick={onCancel}>
                    Cancel
                  </Button>
                </>
              )}
            </Space>
          }
        >
          <Descriptions column={3} size="small" style={{ marginBottom: 12 }}>
            <Descriptions.Item label="Period">
              {String(selected.periodYear)}-{String(selected.periodMonth).padStart(2, '0')}
            </Descriptions.Item>
            <Descriptions.Item label="Employees">{selected.employeeCount}</Descriptions.Item>
            <Descriptions.Item label="Total">
              <Typography.Text strong>
                {selected.totalAmount} {selected.currency}
              </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="Generated">
              {selected.generatedAt ? selected.generatedAt.slice(0, 19).replace('T', ' ') : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Generated by">{selected.generatedBy ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="Pushed to payroll">
              {selected.targetPayrollRunId
                ? payrollMap.get(selected.targetPayrollRunId)?.runNo ?? selected.targetPayrollRunId
                : '—'}
            </Descriptions.Item>
          </Descriptions>
          {selected.status === 'GENERATED' && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message="Items with zero amount are skipped on push"
              description="Items where no matrix rule matched are listed below with a note — fix the matrix or set bonusPercent on the review and re-generate before pushing."
            />
          )}
          <Table
            rowKey="id"
            size="small"
            loading={itemsLoading}
            columns={itemColumns}
            dataSource={items}
            pagination={false}
          />
        </Card>
      )}

      <Modal
        open={genOpen}
        title="Generate bonus run"
        onCancel={() => setGenOpen(false)}
        onOk={() => genForm.submit()}
        okText="Generate"
        width={620}
      >
        <Form
          form={genForm}
          layout="vertical"
          onFinish={onGenerate}
          initialValues={{ currency: 'AZN', period: dayjs() }}
        >
          <Form.Item name="name" label="Run name" rules={[{ required: true }]}>
            <Input placeholder="e.g. Annual 2026 — December bonus" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="cycleId" label="Performance cycle" rules={[{ required: true }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={cycles.map((c) => ({
                    value: c.id,
                    label: `${c.code} — ${c.name} (${c.status})`,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="period" label="Target period" rules={[{ required: true }]}>
                <DatePicker picker="month" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="currency" label="Currency">
                <Input maxLength={3} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="note" label="Note">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Eligible reviews are those in APPROVED / CALIBRATING / COMPLETED state for the chosen
            cycle. Per-employee amounts are derived as: review.bonusPercent (if set) → matrix
            recommendation match → matrix rating band → otherwise 0 with a note.
          </Typography.Text>
        </Form>
      </Modal>

      <Modal
        open={pushOpen}
        title={selected ? `Push ${selected.runNo} to payroll` : 'Push to payroll'}
        onCancel={() => setPushOpen(false)}
        onOk={() => pushForm.submit()}
        okText="Push"
      >
        <Form form={pushForm} layout="vertical" onFinish={onPush}>
          <Form.Item
            name="targetPayrollRunId"
            label="Target payroll run"
            rules={[{ required: true }]}
            tooltip="Bonus items are added as payroll_bonus rows; the payroll engine includes them in the next recalculation."
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={payrollRuns
                .filter((p) => p.status !== 'PAID' && p.status !== 'CLOSED')
                .map((p) => ({
                  value: p.id,
                  label: `${p.runNo} — ${p.periodYear}-${String(p.periodMonth).padStart(2, '0')} (${p.status})`,
                }))}
            />
          </Form.Item>
        </Form>
        {!selected ? <Spin /> : null}
      </Modal>
    </Space>
  )
}
