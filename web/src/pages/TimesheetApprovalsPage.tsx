import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntdApp, Button, Card, Col, DatePicker, Empty, Row, Space,
  Statistic, Table, Tabs, Tag, Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  timesheetApprovalApi, type CorrectionView, type QueueRow,
} from '../api/timesheetApproval'

/**
 * The manager's approval queue.
 *
 * Hierarchy scoping is server-side, so this page simply renders what came back
 * — there is no client-side filter that could be wrong. Bulk approve is offered
 * only for months with nothing to look at; anything with a warning or a
 * returned day has to be opened, which is the point of flagging them.
 */
export function TimesheetApprovalsPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  const raw = params.get('period')
  const parsed = raw ? dayjs(raw, 'YYYY-MM', true) : null
  const period = parsed && parsed.isValid() ? parsed : dayjs().startOf('month')
  const year = period.year()
  const month = period.month() + 1

  const [rows, setRows] = useState<QueueRow[]>([])
  const [corrections, setCorrections] = useState<CorrectionView[]>([])
  const [selected, setSelected] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [queue, pending] = await Promise.all([
        timesheetApprovalApi.queue(year, month),
        timesheetApprovalApi.pendingCorrections().catch(() => [] as CorrectionView[]),
      ])
      setRows(queue)
      setCorrections(pending)
      setSelected([])
    } catch (err) {
      message.error(errorOf(err, 'Could not load the approval queue'))
    } finally {
      setLoading(false)
    }
  }, [year, month, message])

  useEffect(() => { load() }, [load])

  const bulkApprove = async () => {
    setBusy(true)
    try {
      const result = await timesheetApprovalApi.bulkApprove(selected)
      const skipped = Object.entries(result.skipped)
      if (result.approved.length > 0) {
        message.success(`Approved ${result.approved.length} timesheet(s)`)
      }
      // Never let a partial bulk result pass silently — a month that quietly
      // failed to approve is a month that quietly blocks the period lock.
      if (skipped.length > 0) {
        message.warning(`${skipped.length} skipped: ${skipped[0][1]}`)
      }
      await load()
    } catch (err) {
      message.error(errorOf(err, 'Bulk approve failed'))
    } finally {
      setBusy(false)
    }
  }

  const decide = async (id: string, approve: boolean) => {
    setBusy(true)
    try {
      await timesheetApprovalApi.decideCorrection(id, approve)
      message.success(approve ? 'Correction approved — the day is reopened' : 'Correction rejected')
      await load()
    } catch (err) {
      message.error(errorOf(err, 'Could not record the decision'))
    } finally {
      setBusy(false)
    }
  }

  const columns: ColumnsType<QueueRow> = [
    { title: 'Emp No', dataIndex: 'employeeNo', width: 110 },
    { title: 'Employee', dataIndex: 'employeeName' },
    { title: 'Position', dataIndex: 'positionTitle', responsive: ['lg'] },
    { title: 'Hours', dataIndex: 'totalHours', width: 90, align: 'right' },
    { title: 'OT', dataIndex: 'overtimeHours', width: 80, align: 'right' },
    { title: 'Days', width: 90, align: 'right', render: (_, r) => r.daysEntered },
    {
      title: 'Flags', width: 170,
      render: (_, r) => (
        <Space size={4} wrap>
          {r.warnings > 0 && <Tag color="orange">{r.warnings} note{r.warnings > 1 ? 's' : ''}</Tag>}
          {r.daysReturned > 0 && <Tag color="red">{r.daysReturned} returned</Tag>}
          {r.warnings === 0 && r.daysReturned === 0 && <Tag color="green">Clean</Tag>}
        </Space>
      ),
    },
    {
      title: 'Status', dataIndex: 'status', width: 120,
      render: (v: QueueRow['status']) => <Tag color={statusColor(v)}>{v}</Tag>,
    },
    {
      title: '', width: 90,
      render: (_, r) => (
        <Button size="small" type="link" onClick={() => navigate(`/manager/timesheets/${r.timesheetId}`)}>
          Review
        </Button>
      ),
    },
  ]

  const correctionColumns: ColumnsType<CorrectionView> = [
    { title: 'Employee', dataIndex: 'employeeName' },
    { title: 'Day', dataIndex: 'workDate', width: 120,
      render: (v: string) => dayjs(v).format('DD MMM YYYY') },
    { title: 'Currently', dataIndex: 'currentValue' },
    { title: 'Requested', dataIndex: 'requestedValue' },
    { title: 'Reason', dataIndex: 'reason' },
    {
      title: '', width: 170,
      render: (_, r) => (
        <Space>
          <Button size="small" type="primary" loading={busy} onClick={() => decide(r.id, true)}>
            Approve
          </Button>
          <Button size="small" danger loading={busy} onClick={() => decide(r.id, false)}>
            Reject
          </Button>
        </Space>
      ),
    },
  ]

  const clean = rows.filter((r) => r.cleanForBulkApproval)
  const needsAttention = rows.length - clean.length

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Row gutter={[16, 16]} align="middle">
          <Col flex="auto">
            <Typography.Title level={4} style={{ margin: 0 }}>Timesheet Approvals</Typography.Title>
            <Typography.Text type="secondary">
              Your team's submitted months for {period.format('MMMM YYYY')}
            </Typography.Text>
          </Col>
          <Col>
            <DatePicker
              picker="month"
              allowClear={false}
              value={period}
              onChange={(v: Dayjs | null) => {
                if (v) setParams({ period: v.format('YYYY-MM') }, { replace: true })
              }}
            />
          </Col>
        </Row>
      </Card>

      <Row gutter={16}>
        <Col xs={8}><Card><Statistic title="Awaiting you" value={rows.length} /></Card></Col>
        <Col xs={8}><Card><Statistic title="Clean" value={clean.length} /></Card></Col>
        <Col xs={8}>
          <Card>
            <Statistic title="Need a look" value={needsAttention}
                       valueStyle={{ color: needsAttention > 0 ? '#d46b08' : undefined }} />
          </Card>
        </Col>
      </Row>

      <Card>
        <Tabs
          items={[
            {
              key: 'queue',
              label: `Pending approval (${rows.length})`,
              children: (
                <>
                  <Space style={{ marginBottom: 12 }}>
                    <Button
                      type="primary"
                      disabled={selected.length === 0}
                      loading={busy}
                      onClick={bulkApprove}
                    >
                      Approve selected ({selected.length})
                    </Button>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      Only months with no notes and no returned days can be approved in bulk.
                    </Typography.Text>
                  </Space>
                  <Table
                    rowKey="timesheetId"
                    size="small"
                    loading={loading}
                    dataSource={rows}
                    columns={columns}
                    pagination={false}
                    scroll={{ x: 1000 }}
                    rowSelection={{
                      selectedRowKeys: selected,
                      onChange: (keys) => setSelected(keys as string[]),
                      getCheckboxProps: (r) => ({ disabled: !r.cleanForBulkApproval }),
                    }}
                  />
                </>
              ),
            },
            {
              key: 'corrections',
              label: `Correction requests (${corrections.length})`,
              children: corrections.length === 0
                ? <Empty description="No correction requests waiting" />
                : (
                  <>
                    <Alert
                      type="info"
                      showIcon
                      style={{ marginBottom: 12 }}
                      message="Approving a correction reopens only that day"
                      description="The rest of the month keeps the approval it already has."
                    />
                    <Table
                      rowKey="id"
                      size="small"
                      dataSource={corrections}
                      columns={correctionColumns}
                      pagination={false}
                      scroll={{ x: 900 }}
                    />
                  </>
                ),
            },
          ]}
        />
      </Card>
    </Space>
  )
}

export function statusColor(status: string): string {
  switch (status) {
    case 'SUBMITTED': return 'processing'
    case 'RETURNED': return 'red'
    case 'APPROVED': return 'success'
    case 'LOCKED': return 'purple'
    case 'REOPENED': return 'warning'
    default: return 'default'
  }
}

export function errorOf(err: unknown, fallback: string): string {
  const res = (err as { response?: { data?: { message?: string } } })?.response
  return res?.data?.message ?? fallback
}

export default TimesheetApprovalsPage
