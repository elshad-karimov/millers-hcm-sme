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
import {
  permissionApi,
  type PermissionBalance,
  type PermissionType,
} from '../api/permission'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const CURRENT_YEAR = new Date().getFullYear()

export function PermissionBalancesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canAdjust = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [year, setYear] = useState(CURRENT_YEAR)
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [employees, setEmployees] = useState<Employee[]>([])
  const [types, setTypes] = useState<PermissionType[]>([])
  const [balances, setBalances] = useState<PermissionBalance[]>([])
  const [loading, setLoading] = useState(false)
  const [adjustOpen, setAdjustOpen] = useState<PermissionBalance | null>(null)
  const [adjustValues, setAdjustValues] = useState<{ delta: number; reason: string }>({
    delta: 0,
    reason: '',
  })

  // M241 — Same defensive split as LeaveBalancesPage: a Promise.all
  // with no .catch swallows BOTH setters on a single rejection, which
  // is how every column ends up as a raw UUID.
  useEffect(() => {
    permissionApi.types()
      .then(setTypes)
      .catch((err) => message.warning(
        err?.response?.data?.message ?? 'Could not load permission types — names will appear as IDs.'))
    employeesApi.list({ size: 500 })
      .then((r) => setEmployees(r.content))
      .catch((err) => message.warning(
        err?.response?.data?.message ?? 'Could not load employees — names will appear as IDs.'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // M241 — Lazy fill-in for any balance row that references an
  // employee or permission-type outside the initial page-0 slice.
  useEffect(() => {
    if (balances.length === 0) return
    const have = new Set(employees.map((e) => e.id))
    const missing = [...new Set(balances.map((b) => b.employeeId))]
      .filter((id) => !have.has(id))
    if (missing.length === 0) return
    Promise.all(missing.map((id) => employeesApi.get(id).catch(() => null)))
      .then((extras) => {
        const valid = extras.filter((e): e is Employee => e !== null)
        if (valid.length > 0) setEmployees((prev) => [...prev, ...valid])
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [balances, employees.length])

  useEffect(() => {
    if (balances.length === 0) return
    const have = new Set(types.map((t) => t.id))
    const missing = [...new Set(balances.map((b) => b.permissionTypeId))]
      .filter((id) => !have.has(id))
    if (missing.length === 0) return
    Promise.all(missing.map((id) => permissionApi.getType(id).catch(() => null)))
      .then((extras) => {
        const valid = extras.filter((t): t is PermissionType => t !== null)
        if (valid.length > 0) setTypes((prev) => [...prev, ...valid])
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [balances, types.length])

  const load = () => {
    setLoading(true)
    permissionApi
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

  const employeeMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])
  const typeMap = useMemo(() => new Map(types.map((t) => [t.id, t])), [types])

  const columns: ColumnsType<PermissionBalance> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        return e ? `${e.employeeNo} · ${e.firstName} ${e.lastName}` : id
      },
    },
    {
      title: 'Type',
      dataIndex: 'permissionTypeId',
      render: (id: string) => {
        const t = typeMap.get(id)
        return t
          ? <span><Tag color="geekblue">{t.code}</Tag>{t.name}</span>
          : id
      },
      width: 220,
    },
    { title: 'Limit', dataIndex: 'limitHours', width: 100, align: 'right', render: (v: number) => `${v}h` },
    { title: 'Adjustment', dataIndex: 'adjustmentHours', width: 110, align: 'right', render: (v: number) => `${v}h` },
    { title: 'Used', dataIndex: 'usedHours', width: 90, align: 'right', render: (v: number) => `${v}h` },
    { title: 'Reserved', dataIndex: 'reservedHours', width: 100, align: 'right', render: (v: number) => `${v}h` },
    {
      title: 'Remaining',
      dataIndex: 'remainingHours',
      width: 110,
      align: 'right',
      render: (v: number) => <strong>{v}h</strong>,
    },
    canAdjust
      ? {
          title: '',
          width: 90,
          render: (_, r) => (
            <Button
              size="small"
              onClick={() => {
                setAdjustValues({ delta: 0, reason: '' })
                setAdjustOpen(r)
              }}
            >
              Adjust
            </Button>
          ),
        }
      : { title: '', width: 0, render: () => null },
  ]

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Permission balances</Typography.Title>}>
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
          adjustOpen ? `Adjust ${typeMap.get(adjustOpen.permissionTypeId)?.code ?? 'balance'}` : ''
        }
        onCancel={() => setAdjustOpen(null)}
        onOk={async () => {
          if (!adjustOpen) return
          if (!adjustValues.reason.trim()) {
            message.warning('Reason is required')
            return
          }
          try {
            await permissionApi.adjustBalance({
              employeeId: adjustOpen.employeeId,
              permissionTypeId: adjustOpen.permissionTypeId,
              year: adjustOpen.year,
              deltaHours: adjustValues.delta,
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
            Positive deltas credit; negative deltas debit. Hours.
          </Typography.Text>
          <InputNumber
            style={{ width: '100%' }}
            value={adjustValues.delta}
            onChange={(v) => setAdjustValues((s) => ({ ...s, delta: Number(v ?? 0) }))}
            step={0.5}
            addonBefore="Delta (hours)"
          />
          <Input.TextArea
            placeholder="Reason (required)"
            rows={3}
            value={adjustValues.reason}
            onChange={(e) => setAdjustValues((s) => ({ ...s, reason: e.target.value }))}
          />
        </Space>
      </Modal>
    </Card>
  )
}
