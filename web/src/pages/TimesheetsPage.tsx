import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Empty,
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
import { useNavigate } from 'react-router-dom'
import {
  timesheetApi,
  type Timesheet,
  type TimesheetStatus,
} from '../api/timesheet'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const STATUS_COLOR: Record<TimesheetStatus, string> = {
  DRAFT: 'default',
  SUBMITTED: 'gold',
  APPROVED: 'green',
  LOCKED: 'blue',
  REOPENED: 'orange',
}

export function TimesheetsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canGenerate = hasRole(...RoleSets.HR_WRITE)

  const [period, setPeriod] = useState<dayjs.Dayjs>(dayjs())
  const [employees, setEmployees] = useState<Employee[]>([])
  const [rows, setRows] = useState<Timesheet[]>([])
  const [loading, setLoading] = useState(false)
  const [genEmployee, setGenEmployee] = useState<string | undefined>()
  const [generating, setGenerating] = useState(false)

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content))
  }, [])

  const load = () => {
    setLoading(true)
    timesheetApi
      .listForPeriod(period.year(), period.month() + 1)
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load timesheets'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [period])

  const employeeMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])

  const generate = async () => {
    if (!genEmployee) {
      message.warning('Select an employee to generate')
      return
    }
    setGenerating(true)
    try {
      const ts = await timesheetApi.generate(genEmployee, period.year(), period.month() + 1)
      message.success(`Timesheet generated — ${ts.days.length} days`)
      navigate(`/timesheets/${ts.id}`)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Generation failed',
      )
    } finally {
      setGenerating(false)
    }
  }

  const columns: ColumnsType<Timesheet> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        return e ? `${e.employeeNo} ${e.lastName} ${e.firstName}` : id
      },
    },
    {
      title: 'Worked',
      dataIndex: 'totalWorkedHours',
      render: (v: number) => `${v} h`,
      width: 100,
      align: 'right',
    },
    {
      title: 'Overtime',
      dataIndex: 'totalOvertimeHours',
      render: (v: number) => `${v} h`,
      width: 100,
      align: 'right',
    },
    {
      title: 'Leave',
      render: (_, r) => `${r.totalLeaveDays}d`,
      width: 80,
      align: 'right',
    },
    {
      title: 'Sick',
      render: (_, r) => `${r.totalSickDays}d`,
      width: 80,
      align: 'right',
    },
    {
      title: 'BT',
      render: (_, r) => `${r.totalBtDays}d`,
      width: 70,
      align: 'right',
    },
    {
      title: 'Permission',
      render: (_, r) => `${r.totalPermissionHrs}h`,
      width: 110,
      align: 'right',
    },
    {
      title: 'Absent',
      render: (_, r) => `${r.totalAbsentDays}d`,
      width: 90,
      align: 'right',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: TimesheetStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: '',
      width: 80,
      render: (_, r) => (
        <Button size="small" onClick={() => navigate(`/timesheets/${r.id}`)}>
          Open
        </Button>
      ),
    },
  ]

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Timesheets</Typography.Title>}>
      <Space style={{ marginBottom: 12 }} wrap>
        <DatePicker
          picker="month"
          value={period}
          onChange={(v) => v && setPeriod(v)}
          format="YYYY / MM"
          style={{ width: 160 }}
        />
        {canGenerate && (
          <>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Employee to generate"
              style={{ width: 280 }}
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
              value={genEmployee}
              onChange={setGenEmployee}
            />
            <Button type="primary" loading={generating} onClick={generate}>
              Generate
            </Button>
          </>
        )}
      </Space>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : rows.length === 0 ? (
        <Empty description={`No timesheets for ${period.format('YYYY-MM')}`} />
      ) : (
        <Table rowKey="id" columns={columns} dataSource={rows} pagination={false} />
      )}
    </Card>
  )
}
