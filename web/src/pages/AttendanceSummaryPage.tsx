import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  attendanceApi,
  type DailySummary,
  type SummaryStatus,
} from '../api/attendance'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const STATUS_COLOR: Record<SummaryStatus, string> = {
  PRESENT: 'green',
  PARTIAL: 'gold',
  ABSENT: 'red',
  NON_WORKING_DAY: 'default',
  NO_SCHEDULE: 'default',
}

function fmtMinutes(m: number) {
  if (m === 0) return '—'
  const h = Math.floor(m / 60)
  const r = m % 60
  return h > 0 ? `${h}h ${r}m` : `${r}m`
}

export function AttendanceSummaryPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canCorrect = hasRole(...RoleSets.HR_TEAM_WRITE)
  const canRunEngine = hasRole(...RoleSets.HR_WRITE)

  const [employees, setEmployees] = useState<Employee[]>([])
  const [rows, setRows] = useState<DailySummary[]>([])
  const [loading, setLoading] = useState(false)
  const [range, setRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().subtract(7, 'day'),
    dayjs(),
  ])
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [correctOpen, setCorrectOpen] = useState<DailySummary | null>(null)
  const [correctValues, setCorrectValues] = useState<{
    workedMinutes?: number
    lateMinutes?: number
    earlyMinutes?: number
    breakMinutes?: number
    overtimeMinutes?: number
    reason: string
  }>({ reason: '' })

  const load = () => {
    setLoading(true)
    attendanceApi
      .summary({
        fromDate: range[0].format('YYYY-MM-DD'),
        toDate: range[1].format('YYYY-MM-DD'),
        employeeId,
      })
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load summaries'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content))
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range, employeeId])

  const runEngine = async () => {
    try {
      const r = await attendanceApi.runEngine({
        fromDate: range[0].format('YYYY-MM-DD'),
        toDate: range[1].format('YYYY-MM-DD'),
        employeeId,
      })
      message.success(
        `Processed ${r.employeesProcessed} employees, wrote ${r.summariesWritten} summaries`,
      )
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Engine run failed',
      )
    }
  }

  const employeeMap = new Map(employees.map((e) => [e.id, e]))

  const columns: ColumnsType<DailySummary> = [
    { title: 'Date', dataIndex: 'workDate', width: 110 },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        return e ? `${e.employeeNo} ${e.lastName}` : id
      },
    },
    {
      title: 'Schedule',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <span>
            {r.scheduleStart && r.scheduleEnd
              ? `${r.scheduleStart.slice(0, 5)}–${r.scheduleEnd.slice(0, 5)}`
              : '—'}
          </span>
          {r.source === 'ROSTER' && (
            <Tag color="blue" style={{ marginInlineEnd: 0, fontSize: 10 }}>
              roster
            </Tag>
          )}
          {r.source === 'NONE' && (
            <Tag style={{ marginInlineEnd: 0, fontSize: 10 }}>none</Tag>
          )}
        </Space>
      ),
      width: 110,
    },
    {
      title: 'In / Out',
      render: (_, r) =>
        r.entryTime || r.exitTime ? (
          <Space direction="vertical" size={0}>
            <span>{r.entryTime ? dayjs(r.entryTime).format('HH:mm') : '—'}</span>
            <span>{r.exitTime ? dayjs(r.exitTime).format('HH:mm') : '—'}</span>
          </Space>
        ) : (
          '—'
        ),
      width: 90,
    },
    { title: 'Worked', dataIndex: 'workedMinutes', render: fmtMinutes, width: 90 },
    { title: 'Late', dataIndex: 'lateMinutes', render: fmtMinutes, width: 80 },
    { title: 'Early', dataIndex: 'earlyMinutes', render: fmtMinutes, width: 80 },
    { title: 'OT', dataIndex: 'overtimeMinutes', render: fmtMinutes, width: 80 },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: SummaryStatus) => (
        <Tag color={STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>
      ),
      width: 130,
    },
    {
      title: 'Corrected',
      render: (_, r) =>
        r.correctedAt ? (
          <Tag color="purple">
            {r.correctedBy} · {dayjs(r.correctedAt).format('MM-DD HH:mm')}
          </Tag>
        ) : (
          '—'
        ),
    },
    canCorrect
      ? {
          title: '',
          width: 90,
          render: (_, r) => (
            <Button
              size="small"
              onClick={() => {
                setCorrectOpen(r)
                setCorrectValues({
                  workedMinutes: r.workedMinutes,
                  lateMinutes: r.lateMinutes,
                  earlyMinutes: r.earlyMinutes,
                  breakMinutes: r.breakMinutes,
                  overtimeMinutes: r.overtimeMinutes,
                  reason: '',
                })
              }}
            >
              Correct
            </Button>
          ),
        }
      : { title: '', width: 0, render: () => null },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Daily summary</Typography.Title>}
      extra={
        canRunEngine && (
          <Popconfirm
            title="Run attendance engine?"
            description={`Computes summaries for ${range[0].format('YYYY-MM-DD')} → ${range[1].format(
              'YYYY-MM-DD',
            )}${employeeId ? ' (one employee)' : ' (all employees with a schedule)'}.`}
            onConfirm={runEngine}
          >
            <Button type="primary">Run engine</Button>
          </Popconfirm>
        )
      }
    >
      <Space style={{ marginBottom: 12 }} wrap>
        <DatePicker.RangePicker
          value={range}
          onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])}
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
          onChange={(v) => setEmployeeId(v)}
        />
      </Space>

      <Table rowKey="id" columns={columns} dataSource={rows} loading={loading} pagination={false} />

      <Modal
        open={!!correctOpen}
        title={correctOpen ? `Correct ${correctOpen.workDate}` : ''}
        onCancel={() => setCorrectOpen(null)}
        onOk={async () => {
          if (!correctOpen) return
          if (!correctValues.reason.trim()) {
            message.warning('Reason is required')
            return
          }
          try {
            await attendanceApi.correct(correctOpen.id, correctValues)
            message.success('Correction recorded')
            setCorrectOpen(null)
            load()
          } catch (err) {
            message.error(
              (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
                'Correction failed',
            )
          }
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space.Compact block>
            <InputNumber
              addonBefore="Worked"
              addonAfter="min"
              min={0}
              value={correctValues.workedMinutes}
              onChange={(v) =>
                setCorrectValues((s) => ({ ...s, workedMinutes: v ?? undefined }))
              }
            />
            <InputNumber
              addonBefore="OT"
              addonAfter="min"
              min={0}
              value={correctValues.overtimeMinutes}
              onChange={(v) =>
                setCorrectValues((s) => ({ ...s, overtimeMinutes: v ?? undefined }))
              }
            />
          </Space.Compact>
          <Space.Compact block>
            <InputNumber
              addonBefore="Late"
              addonAfter="min"
              min={0}
              value={correctValues.lateMinutes}
              onChange={(v) =>
                setCorrectValues((s) => ({ ...s, lateMinutes: v ?? undefined }))
              }
            />
            <InputNumber
              addonBefore="Early"
              addonAfter="min"
              min={0}
              value={correctValues.earlyMinutes}
              onChange={(v) =>
                setCorrectValues((s) => ({ ...s, earlyMinutes: v ?? undefined }))
              }
            />
            <InputNumber
              addonBefore="Break"
              addonAfter="min"
              min={0}
              value={correctValues.breakMinutes}
              onChange={(v) =>
                setCorrectValues((s) => ({ ...s, breakMinutes: v ?? undefined }))
              }
            />
          </Space.Compact>
          <Input.TextArea
            placeholder="Reason (required)"
            rows={3}
            value={correctValues.reason}
            onChange={(e) => setCorrectValues((s) => ({ ...s, reason: e.target.value }))}
          />
        </Space>
      </Modal>
    </Card>
  )
}
