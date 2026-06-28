import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Input,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { leaveApi, type LeaveBalance, type LeaveType, type LedgerEntry } from '../api/leave'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const CURRENT_YEAR = new Date().getFullYear()

export function LeaveBalancesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canAdjust = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [year, setYear] = useState(CURRENT_YEAR)
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [employees, setEmployees] = useState<Employee[]>([])
  const [types, setTypes] = useState<LeaveType[]>([])
  const [balances, setBalances] = useState<LeaveBalance[]>([])
  const [loading, setLoading] = useState(false)
  const [adjustOpen, setAdjustOpen] = useState<LeaveBalance | null>(null)
  const [adjustValues, setAdjustValues] = useState<{ delta: number; reason: string }>({
    delta: 0,
    reason: '',
  })
  const [ledgerOpen, setLedgerOpen] = useState<{
    employeeId: string
    leaveTypeId: string
    year: number
  } | null>(null)
  const [ledgerRows, setLedgerRows] = useState<LedgerEntry[]>([])
  const [ledgerLoading, setLedgerLoading] = useState(false)

  // M241 — Split fetches with individual catches. The previous
  // Promise.all swallowed both setters if EITHER call rejected, which
  // is exactly how the table ended up rendering raw UUIDs: an HTTP
  // error on /employees left both employees AND types empty.
  useEffect(() => {
    leaveApi.types()
      .then(setTypes)
      .catch((err) => message.warning(
        err?.response?.data?.message ?? 'Could not load leave types — names will appear as IDs.'))
    employeesApi.list({ size: 500 })
      .then((r) => setEmployees(r.content))
      .catch((err) => message.warning(
        err?.response?.data?.message ?? 'Could not load employees — names will appear as IDs.'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // M241 — Lazy fill-in: any balance row that references an
  // employee/type ID we don't have yet (e.g. one beyond the 500-row
  // initial page, or one not returned because of ABAC scoping) is
  // fetched by ID and merged in. Keeps the table honest no matter what
  // upstream returned.
  useEffect(() => {
    if (balances.length === 0) return
    const have = new Set(employees.map((e) => e.id))
    const missing = [...new Set(balances.map((b) => b.employeeId))]
      .filter((id) => !have.has(id))
    if (missing.length === 0) return
    Promise.all(missing.map((id) =>
      employeesApi.get(id).catch(() => null),
    )).then((extras) => {
      const valid = extras.filter((e): e is Employee => e !== null)
      if (valid.length > 0) setEmployees((prev) => [...prev, ...valid])
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [balances, employees.length])

  useEffect(() => {
    if (balances.length === 0) return
    const have = new Set(types.map((t) => t.id))
    const missing = [...new Set(balances.map((b) => b.leaveTypeId))]
      .filter((id) => !have.has(id))
    if (missing.length === 0) return
    Promise.all(missing.map((id) =>
      leaveApi.getType(id).catch(() => null),
    )).then((extras) => {
      const valid = extras.filter((t): t is LeaveType => t !== null)
      if (valid.length > 0) setTypes((prev) => [...prev, ...valid])
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [balances, types.length])

  const load = () => {
    setLoading(true)
    leaveApi
      .balances({ employeeId, year })
      .then(setBalances)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load balances'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [year, employeeId])

  useEffect(() => {
    if (!ledgerOpen) return
    setLedgerLoading(true)
    leaveApi
      .balanceLedger(ledgerOpen.employeeId, ledgerOpen.year, ledgerOpen.leaveTypeId)
      .then(setLedgerRows)
      .catch(() => setLedgerRows([]))
      .finally(() => setLedgerLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ledgerOpen])

  const employeeMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])
  const typeMap = useMemo(() => new Map(types.map((t) => [t.id, t])), [types])

  const columns: ColumnsType<LeaveBalance> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        // EMP-00002 · Rashad Aliyev — natural first-last order; the
        // last-resort fallback to the raw UUID stays so a stale row
        // can still be told apart instead of rendering blank.
        return e ? `${e.employeeNo} · ${e.firstName} ${e.lastName}` : id
      },
    },
    {
      title: 'Leave type',
      dataIndex: 'leaveTypeId',
      render: (id: string) => {
        const t = typeMap.get(id)
        // Show both code AND name so the demo audience doesn't have
        // to mentally decode "ANNUAL" vs "MARRIAGE" etc.
        return t
          ? <span><Tag color="geekblue">{t.code}</Tag>{t.name}</span>
          : id
      },
      width: 220,
    },
    { title: 'Entitlement', dataIndex: 'entitlementDays', width: 110, align: 'right' },
    { title: 'Carry-fwd', dataIndex: 'carriedForwardDays', width: 110, align: 'right' },
    { title: 'Adjustment', dataIndex: 'adjustmentDays', width: 110, align: 'right' },
    { title: 'Used', dataIndex: 'usedDays', width: 90, align: 'right' },
    { title: 'Reserved', dataIndex: 'reservedDays', width: 100, align: 'right' },
    {
      title: 'Remaining',
      dataIndex: 'remainingDays',
      width: 110,
      align: 'right',
      render: (v: number) => <strong>{v}</strong>,
    },
    {
      title: '',
      width: canAdjust ? 140 : 80,
      render: (_, r) => (
        <Space>
          <Button
            size="small"
            onClick={() => {
              setLedgerOpen({
                employeeId: r.employeeId,
                leaveTypeId: r.leaveTypeId,
                year: r.year,
              })
            }}
          >
            Ledger
          </Button>
          {canAdjust && (
            <Button
              size="small"
              onClick={() => {
                setAdjustValues({ delta: 0, reason: '' })
                setAdjustOpen(r)
              }}
            >
              Adjust
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Leave balances</Typography.Title>}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          style={{ width: 110 }}
          value={year}
          options={Array.from({ length: 5 }, (_, i) => CURRENT_YEAR - 2 + i).map((y) => ({
            value: y,
            label: String(y),
          }))}
          onChange={setYear}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="All employees"
          style={{ width: 280 }}
          options={employees.map((e) => ({
            value: e.id,
            label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
          }))}
          value={employeeId}
          onChange={setEmployeeId}
        />
      </Space>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={balances}
        loading={loading}
        pagination={false}
      />

      <Modal
        open={!!adjustOpen}
        title={
          adjustOpen
            ? `Adjust ${typeMap.get(adjustOpen.leaveTypeId)?.code ?? 'balance'}`
            : ''
        }
        onCancel={() => setAdjustOpen(null)}
        onOk={async () => {
          if (!adjustOpen) return
          if (!adjustValues.reason.trim()) {
            message.warning('Reason is required')
            return
          }
          try {
            await leaveApi.adjustBalance({
              employeeId: adjustOpen.employeeId,
              leaveTypeId: adjustOpen.leaveTypeId,
              year: adjustOpen.year,
              deltaDays: adjustValues.delta,
              reason: adjustValues.reason,
            })
            message.success('Balance adjusted')
            setAdjustOpen(null)
            load()
          } catch (err) {
            message.error(
              (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
                'Adjustment failed',
            )
          }
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text type="secondary">
            Positive deltas credit the employee; negative deltas debit. The balance row records
            the running total of all adjustments.
          </Typography.Text>
          <InputNumber
            style={{ width: '100%' }}
            value={adjustValues.delta}
            onChange={(v) => setAdjustValues((s) => ({ ...s, delta: Number(v ?? 0) }))}
            step={0.5}
            addonBefore="Delta (days)"
          />
          <Input.TextArea
            placeholder="Reason (required)"
            rows={3}
            value={adjustValues.reason}
            onChange={(e) => setAdjustValues((s) => ({ ...s, reason: e.target.value }))}
          />
        </Space>
      </Modal>

      <Modal
        title="Balance Ledger"
        open={!!ledgerOpen}
        onCancel={() => setLedgerOpen(null)}
        footer={null}
        width={760}
        destroyOnHidden
      >
        <Table
          rowKey="id"
          size="small"
          loading={ledgerLoading}
          dataSource={ledgerRows}
          pagination={false}
          columns={[
            { title: 'Date', dataIndex: 'effectiveDate', width: 110 },
            {
              title: 'Type',
              dataIndex: 'txType',
              width: 140,
              render: (v: string) => <Tag>{v.replace(/_/g, ' ')}</Tag>,
            },
            {
              title: 'Amount',
              dataIndex: 'amount',
              width: 90,
              render: (v: number) => (
                <span style={{ color: v >= 0 ? '#52c41a' : '#ff4d4f' }}>
                  {v >= 0 ? '+' : ''}
                  {v}
                </span>
              ),
            },
            { title: 'Balance After', dataIndex: 'balanceAfter', width: 110 },
            {
              title: 'Notes',
              dataIndex: 'notes',
              render: (v?: string | null) => v ?? '—',
            },
            { title: 'By', dataIndex: 'createdBy', width: 120 },
          ]}
        />
      </Modal>
    </Card>
  )
}
