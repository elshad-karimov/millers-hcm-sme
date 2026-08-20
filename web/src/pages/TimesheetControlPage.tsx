import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntdApp, Button, Card, Col, DatePicker, Input, Modal, Row,
  Space, Statistic, Table, Tag, Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { LockOutlined, UnlockOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { useSearchParams } from 'react-router-dom'
import { timesheetControlApi, type ControlBoard, type ControlRow } from '../api/timesheetApproval'
import { errorOf, statusColor } from './TimesheetApprovalsPage'

/**
 * HR's view of a whole period, and the lock that closes it.
 *
 * "Payroll ready" here means decided and closed — not priced. The lock is the
 * gate payroll waits behind, and the server refuses it while anything is still
 * awaiting a decision, so this page states plainly what is in the way rather
 * than offering a button that will fail.
 */
export function TimesheetControlPage() {
  const { message } = AntdApp.useApp()
  const [params, setParams] = useSearchParams()

  const raw = params.get('period')
  const parsed = raw ? dayjs(raw, 'YYYY-MM', true) : null
  const period = parsed && parsed.isValid() ? parsed : dayjs().startOf('month')
  const year = period.year()
  const month = period.month() + 1

  const [board, setBoard] = useState<ControlBoard | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [lockOpen, setLockOpen] = useState(false)
  const [unlockOpen, setUnlockOpen] = useState(false)
  const [reason, setReason] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setBoard(await timesheetControlApi.board(year, month))
    } catch (err) {
      message.error(errorOf(err, 'Could not load the control board'))
    } finally {
      setLoading(false)
    }
  }, [year, month, message])

  useEffect(() => { load() }, [load])

  const act = async (run: () => Promise<ControlBoard>, success: string) => {
    setBusy(true)
    try {
      setBoard(await run())
      message.success(success)
      setLockOpen(false)
      setUnlockOpen(false)
      setReason('')
    } catch (err) {
      message.error(errorOf(err, 'Action failed'))
    } finally {
      setBusy(false)
    }
  }

  const columns: ColumnsType<ControlRow> = [
    { title: 'Emp No', dataIndex: 'employeeNo', width: 110 },
    { title: 'Employee', dataIndex: 'employeeName' },
    { title: 'Hours', dataIndex: 'totalHours', width: 90, align: 'right' },
    {
      title: 'Status', dataIndex: 'status', width: 120,
      render: (v: string) => <Tag color={statusColor(v)}>{v}</Tag>,
    },
    {
      title: 'Notes', dataIndex: 'warnings', width: 90, align: 'right',
      render: (v: number) => (v > 0 ? <Tag color="orange">{v}</Tag> : '—'),
    },
    {
      title: 'Exception', dataIndex: 'exception',
      render: (v: string | null) => v ?? <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'Payroll ready', dataIndex: 'payrollReady', width: 130,
      render: (v: boolean) => v ? <Tag color="green">Ready</Tag> : <Tag color="red">Blocked</Tag>,
    },
  ]

  const locked = board?.periodStatus === 'LOCKED'

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Row gutter={[16, 16]} align="middle">
          <Col flex="auto">
            <Typography.Title level={4} style={{ margin: 0 }}>Timesheet Control</Typography.Title>
            <Space size={8} style={{ marginTop: 6 }}>
              <DatePicker
                picker="month"
                allowClear={false}
                value={period}
                onChange={(v: Dayjs | null) => {
                  if (v) setParams({ period: v.format('YYYY-MM') }, { replace: true })
                }}
              />
              {board && (
                <Tag color={locked ? 'purple' : 'green'} icon={locked ? <LockOutlined /> : <UnlockOutlined />}>
                  {board.periodStatus}
                </Tag>
              )}
              {board?.lockedAt && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  Locked {dayjs(board.lockedAt).format('D MMM YYYY HH:mm')}
                  {board.lockedBy ? ` by ${board.lockedBy}` : ''}
                </Typography.Text>
              )}
            </Space>
          </Col>
          <Col>
            {locked ? (
              <Button icon={<UnlockOutlined />} onClick={() => setUnlockOpen(true)} loading={busy}>
                Unlock period
              </Button>
            ) : (
              <Button
                type="primary"
                icon={<LockOutlined />}
                disabled={!board?.lockable}
                onClick={() => setLockOpen(true)}
              >
                Lock period
              </Button>
            )}
          </Col>
        </Row>
      </Card>

      {board && !locked && board.lockBlockedReason && (
        <Alert
          type="warning"
          showIcon
          message="This period cannot be locked yet"
          description={`${board.lockBlockedReason} Payroll must not consume a period with undecided timesheets.`}
        />
      )}
      {locked && (
        <Alert
          type="success"
          showIcon
          message="Period is locked"
          description="Employees cannot edit and managers cannot approve. Changes now require a correction request."
        />
      )}

      <Row gutter={16}>
        <Col xs={8} md={4}><Card><Statistic title="Employees" value={board?.employees ?? 0} /></Card></Col>
        <Col xs={8} md={4}><Card><Statistic title="Draft" value={board?.draft ?? 0} /></Card></Col>
        <Col xs={8} md={4}>
          <Card>
            <Statistic title="Submitted" value={board?.submitted ?? 0}
                       valueStyle={{ color: (board?.submitted ?? 0) > 0 ? '#d46b08' : undefined }} />
          </Card>
        </Col>
        <Col xs={8} md={4}>
          <Card>
            <Statistic title="Returned" value={board?.returned ?? 0}
                       valueStyle={{ color: (board?.returned ?? 0) > 0 ? '#cf1322' : undefined }} />
          </Card>
        </Col>
        <Col xs={8} md={4}><Card><Statistic title="Approved" value={board?.approved ?? 0} /></Card></Col>
        <Col xs={8} md={4}>
          <Card>
            <Statistic title="Payroll ready" value={board?.payrollReady ?? 0}
                       valueStyle={{ color: '#389e0d' }} />
          </Card>
        </Col>
      </Row>

      <Card>
        <Table
          rowKey="timesheetId"
          size="small"
          loading={loading}
          dataSource={board?.rows ?? []}
          columns={columns}
          pagination={{ pageSize: 25, showSizeChanger: false }}
          scroll={{ x: 900 }}
        />
      </Card>

      <Modal
        open={lockOpen}
        title={`Lock ${period.format('MMMM YYYY')}`}
        okText="Lock period"
        okButtonProps={{ loading: busy }}
        onOk={() => act(() => timesheetControlApi.lock(year, month, reason), 'Period locked')}
        onCancel={() => setLockOpen(false)}
      >
        <Typography.Paragraph type="secondary">
          Locking stops all employee edits and manager approvals for this period,
          and marks approved months as final. Payroll may then consume them.
        </Typography.Paragraph>
        <Input.TextArea rows={2} placeholder="Reason (optional)"
                        value={reason} onChange={(e) => setReason(e.target.value)} />
      </Modal>

      <Modal
        open={unlockOpen}
        title={`Unlock ${period.format('MMMM YYYY')}`}
        okText="Unlock period"
        okButtonProps={{ danger: true, disabled: !reason.trim(), loading: busy }}
        onOk={() => act(() => timesheetControlApi.unlock(year, month, reason), 'Period unlocked')}
        onCancel={() => setUnlockOpen(false)}
      >
        <Typography.Paragraph type="secondary">
          Unlocking is an auditable event and requires a reason. Locked months
          return to approved — nobody's month is un-approved by this.
        </Typography.Paragraph>
        <Input.TextArea rows={2} placeholder="Why is this period being reopened?"
                        value={reason} onChange={(e) => setReason(e.target.value)} />
      </Modal>
    </Space>
  )
}

export default TimesheetControlPage
