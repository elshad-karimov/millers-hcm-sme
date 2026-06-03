import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Descriptions,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
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
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  timesheetApi,
  type DayCorrectionRequest,
  type Timesheet,
  type TimesheetCode,
  type TimesheetDay,
  type TimesheetStatus,
} from '../api/timesheet'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { WorkflowPanel } from '../components/WorkflowPanel'
import { RoleSets } from '../auth/roleSets'

const STATUS_COLOR: Record<TimesheetStatus, string> = {
  DRAFT: 'default',
  SUBMITTED: 'gold',
  APPROVED: 'green',
  LOCKED: 'blue',
  REOPENED: 'orange',
}

const CODE_COLOR: Record<TimesheetCode, string> = {
  W: 'green',
  L: 'gold',
  S: 'volcano',
  BT: 'geekblue',
  P: 'cyan',
  O: 'purple',
  H: 'default',
  A: 'red',
}

const CODE_OPTIONS: TimesheetCode[] = ['W', 'L', 'S', 'BT', 'P', 'O', 'H', 'A']

export function TimesheetDetailPage() {
  const { id = '' } = useParams()
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole(...RoleSets.HR_TEAM_WRITE)

  const [ts, setTs] = useState<Timesheet | null>(null)
  const [employee, setEmployee] = useState<Employee | null>(null)
  const [loading, setLoading] = useState(true)
  const [correctOpen, setCorrectOpen] = useState<TimesheetDay | null>(null)
  const [correction, setCorrection] = useState<DayCorrectionRequest>({ reason: '' })

  const load = async () => {
    setLoading(true)
    try {
      const t = await timesheetApi.get(id)
      setTs(t)
      const list = await employeesApi.list({ size: 500 })
      setEmployee(list.content.find((e) => e.id === t.employeeId) ?? null)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to load timesheet',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  if (loading || !ts) {
    return (
      <div style={{ textAlign: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  const submitTimesheet = async () => {
    try {
      const updated = await timesheetApi.submit(ts.id)
      setTs((s) => (s ? { ...s, status: updated.status, workflowInstanceId: updated.workflowInstanceId } : s))
      message.success('Submitted — workflow started')
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Submit failed',
      )
    }
  }

  const regenerate = async () => {
    try {
      const refreshed = await timesheetApi.generate(ts.employeeId, ts.periodYear, ts.periodMonth)
      setTs(refreshed)
      message.success('Regenerated from current source data')
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Regeneration failed',
      )
    }
  }

  const columns: ColumnsType<TimesheetDay> = [
    {
      title: 'Date',
      dataIndex: 'workDate',
      render: (v: string) => `${v} (${dayjs(v).format('ddd')})`,
      width: 130,
    },
    {
      title: 'Code',
      dataIndex: 'primaryCode',
      render: (c: TimesheetCode) => <Tag color={CODE_COLOR[c]}>{c}</Tag>,
      width: 70,
    },
    { title: 'Worked', dataIndex: 'workedHours', render: (v: number) => `${v}h`, width: 90, align: 'right' },
    { title: 'OT', dataIndex: 'overtimeHours', render: (v: number) => `${v}h`, width: 80, align: 'right' },
    { title: 'Break', dataIndex: 'breakHours', render: (v: number) => `${v}h`, width: 80, align: 'right' },
    {
      title: 'Late / Early',
      render: (_, r) =>
        r.lateMinutes || r.earlyMinutes ? `${r.lateMinutes}m / ${r.earlyMinutes}m` : '—',
      width: 110,
    },
    {
      title: 'Anomalies',
      dataIndex: 'anomalies',
      render: (v?: string | null) =>
        v ? (
          <Space size={4} wrap>
            {v.split(',').map((a) => (
              <Tag key={a} color="red">
                {a}
              </Tag>
            ))}
          </Space>
        ) : (
          '—'
        ),
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
    canEdit && ts.status !== 'LOCKED' && ts.status !== 'APPROVED'
      ? {
          title: '',
          width: 90,
          render: (_, r) => (
            <Button
              size="small"
              onClick={() => {
                setCorrectOpen(r)
                setCorrection({
                  primaryCode: r.primaryCode,
                  workedHours: r.workedHours,
                  overtimeHours: r.overtimeHours,
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
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title={
          <Space>
            <Link to="/timesheets">Timesheets</Link> /{' '}
            <strong>
              {ts.periodYear}/{String(ts.periodMonth).padStart(2, '0')}
            </strong>
            {employee && (
              <Tag color="geekblue">
                {employee.employeeNo} — {employee.firstName} {employee.lastName}
              </Tag>
            )}
            <Tag color={STATUS_COLOR[ts.status]}>{ts.status}</Tag>
          </Space>
        }
        extra={
          canEdit && (
            <Space>
              {(ts.status === 'DRAFT' || ts.status === 'REOPENED') && (
                <Popconfirm
                  title="Regenerate the timesheet?"
                  description="Existing correction marks on days will be replaced."
                  onConfirm={regenerate}
                >
                  <Button>Regenerate</Button>
                </Popconfirm>
              )}
              {(ts.status === 'DRAFT' || ts.status === 'REOPENED') && (
                <Button type="primary" onClick={submitTimesheet}>
                  Submit for approval
                </Button>
              )}
              <Button onClick={() => navigate('/timesheets')}>Back to list</Button>
            </Space>
          )
        }
      >
        <Descriptions size="small" column={4} bordered style={{ marginBottom: 16 }}>
          <Descriptions.Item label="Worked hours">{ts.totalWorkedHours}</Descriptions.Item>
          <Descriptions.Item label="Overtime hours">{ts.totalOvertimeHours}</Descriptions.Item>
          <Descriptions.Item label="Leave days">{ts.totalLeaveDays}</Descriptions.Item>
          <Descriptions.Item label="Sick days">{ts.totalSickDays}</Descriptions.Item>
          <Descriptions.Item label="BT days">{ts.totalBtDays}</Descriptions.Item>
          <Descriptions.Item label="Permission hours">{ts.totalPermissionHrs}</Descriptions.Item>
          <Descriptions.Item label="Absent days">{ts.totalAbsentDays}</Descriptions.Item>
          <Descriptions.Item label="Generated">
            {dayjs(ts.generatedAt).format('YYYY-MM-DD HH:mm')} by {ts.generatedBy ?? '—'}
          </Descriptions.Item>
        </Descriptions>
        <Table rowKey="id" columns={columns} dataSource={ts.days} pagination={false} size="small" />
      </Card>

      {ts.status !== 'DRAFT' && (
        <Card title="Workflow">
          <WorkflowPanel
            module="TIMESHEET"
            entity="Timesheet"
            subjectId={ts.id}
            onChanged={() => load()}
          />
        </Card>
      )}

      <Modal
        open={!!correctOpen}
        title={correctOpen ? `Correct ${correctOpen.workDate}` : ''}
        onCancel={() => setCorrectOpen(null)}
        onOk={async () => {
          if (!correctOpen) return
          if (!correction.reason.trim()) {
            message.warning('Reason is required')
            return
          }
          try {
            await timesheetApi.correctDay(ts.id, correctOpen.id, correction)
            message.success('Day corrected')
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
          <Select
            style={{ width: '100%' }}
            value={correction.primaryCode}
            options={CODE_OPTIONS.map((c) => ({ value: c, label: c }))}
            onChange={(v) => setCorrection((s) => ({ ...s, primaryCode: v }))}
          />
          <Space.Compact block>
            <InputNumber
              addonBefore="Worked"
              addonAfter="h"
              min={0}
              step={0.5}
              value={correction.workedHours}
              onChange={(v) =>
                setCorrection((s) => ({ ...s, workedHours: v === null ? undefined : Number(v) }))
              }
            />
            <InputNumber
              addonBefore="OT"
              addonAfter="h"
              min={0}
              step={0.5}
              value={correction.overtimeHours}
              onChange={(v) =>
                setCorrection((s) => ({ ...s, overtimeHours: v === null ? undefined : Number(v) }))
              }
            />
          </Space.Compact>
          <Input.TextArea
            placeholder="Reason (required)"
            rows={3}
            value={correction.reason}
            onChange={(e) => setCorrection((s) => ({ ...s, reason: e.target.value }))}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Corrected days are flagged in audit and excluded from auto-regeneration overrides.
          </Typography.Text>
        </Space>
      </Modal>
    </Space>
  )
}
