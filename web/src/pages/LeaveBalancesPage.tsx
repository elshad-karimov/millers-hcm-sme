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
import { leaveApi, type LeaveBalance, type LeaveType } from '../api/leave'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'

const CURRENT_YEAR = new Date().getFullYear()

export function LeaveBalancesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canAdjust = hasRole('HR_ADMIN', 'SYSTEM_ADMIN')

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

  useEffect(() => {
    Promise.all([leaveApi.types(), employeesApi.list({ size: 500 })]).then(([t, e]) => {
      setTypes(t)
      setEmployees(e.content)
    })
  }, [])

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

  const employeeMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])
  const typeMap = useMemo(() => new Map(types.map((t) => [t.id, t])), [types])

  const columns: ColumnsType<LeaveBalance> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        return e ? `${e.employeeNo} — ${e.lastName} ${e.firstName}` : id
      },
    },
    {
      title: 'Leave type',
      dataIndex: 'leaveTypeId',
      render: (id: string) => {
        const t = typeMap.get(id)
        return t ? <Tag color="geekblue">{t.code}</Tag> : id
      },
      width: 130,
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
    </Card>
  )
}
