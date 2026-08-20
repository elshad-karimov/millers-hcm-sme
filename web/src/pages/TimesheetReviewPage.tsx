import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntdApp, Button, Card, Col, Descriptions, Input, Modal, Row,
  Space, Spin, Statistic, Table, Tag, Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { ArrowLeftOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { useNavigate, useParams } from 'react-router-dom'
import { timesheetApprovalApi, type ReviewDay, type ReviewView } from '../api/timesheetApproval'
import { errorOf, statusColor } from './TimesheetApprovalsPage'

/**
 * One employee's month, as the approver sees it.
 *
 * The variance column is the substance: the manager is judging a declaration
 * against whatever evidence exists, and for offshore crews there often is none.
 * Days with no attendance record say so plainly rather than showing a zero that
 * reads like a discrepancy.
 */
export function TimesheetReviewPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()

  const [data, setData] = useState<ReviewView | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [selected, setSelected] = useState<string[]>([])
  const [returnOpen, setReturnOpen] = useState(false)
  const [rejectOpen, setRejectOpen] = useState(false)
  const [reason, setReason] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await timesheetApprovalApi.review(id))
      setSelected([])
    } catch (err) {
      message.error(errorOf(err, 'Could not load the timesheet'))
    } finally {
      setLoading(false)
    }
  }, [id, message])

  useEffect(() => { load() }, [load])

  const act = async (run: () => Promise<ReviewView>, success: string) => {
    setBusy(true)
    try {
      setData(await run())
      message.success(success)
      setReturnOpen(false)
      setRejectOpen(false)
      setReason('')
      setSelected([])
      return true
    } catch (err) {
      message.error(errorOf(err, 'Could not record the decision'))
      return false
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 64 }}><Spin size="large" /></div>
  }
  if (!data) return null

  const columns: ColumnsType<ReviewDay> = [
    { title: 'Date', dataIndex: 'date', width: 130,
      render: (v: string) => dayjs(v).format('DD MMM ddd') },
    { title: 'Work type', dataIndex: 'workType', width: 130 },
    { title: 'Entered', dataIndex: 'enteredHours', width: 90, align: 'right',
      render: (v: number) => `${v} h` },
    {
      title: 'Attendance', width: 120, align: 'right',
      render: (_, r) => r.attendanceHours == null
        ? <Typography.Text type="secondary">no record</Typography.Text>
        : `${r.attendanceHours} h`,
    },
    {
      title: 'Variance', width: 110, align: 'right',
      render: (_, r) => {
        if (r.varianceHours == null) return <Typography.Text type="secondary">—</Typography.Text>
        const v = Number(r.varianceHours)
        if (v === 0) return '0'
        return <Tag color={Math.abs(v) > 0.5 ? 'orange' : 'default'}>{v > 0 ? '+' : ''}{v} h</Tag>
      },
    },
    {
      title: 'Calculated', width: 240,
      render: (_, r) => {
        const derived = r.quantities.filter((q) => q.derived)
        if (derived.length === 0) return <Typography.Text type="secondary">—</Typography.Text>
        return (
          <Space size={4} wrap>
            {derived.map((q) => <Tag key={q.categoryCode}>{q.categoryName}: {q.quantity}</Tag>)}
          </Space>
        )
      },
    },
    {
      title: 'Employee note', dataIndex: 'employeeNote',
      render: (v: string | null, r) => v || r.varianceExplanation ||
        <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'State', dataIndex: 'approvalState', width: 110,
      render: (v: ReviewDay['approvalState'], r) =>
        v === 'RETURNED'
          ? <Tag color="red" title={r.returnReason ?? undefined}>Returned</Tag>
          : v === 'APPROVED' ? <Tag color="green">Approved</Tag> : <Tag>Pending</Tag>,
    },
  ]

  const returnedDays = data.days.filter((d) => d.approvalState === 'RETURNED').length

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Row gutter={[16, 16]} align="middle">
          <Col flex="auto">
            <Button type="text" icon={<ArrowLeftOutlined />} style={{ paddingLeft: 0 }}
                    onClick={() => navigate('/manager/timesheets')}>
              Back to approvals
            </Button>
            <Typography.Title level={4} style={{ margin: '4px 0 0' }}>
              {data.employeeName ?? 'Employee'}
            </Typography.Title>
            <Space size={8} style={{ marginTop: 4 }}>
              <Typography.Text type="secondary">
                {[data.employeeNo, data.positionTitle].filter(Boolean).join(' · ')}
              </Typography.Text>
              <Tag color={statusColor(data.status)}>{data.status}</Tag>
              {data.submittedAt && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  Submitted {dayjs(data.submittedAt).format('D MMM YYYY HH:mm')}
                </Typography.Text>
              )}
            </Space>
          </Col>
          <Col>
            <Space>
              <Button danger disabled={!data.actionable || busy}
                      onClick={() => setRejectOpen(true)}>Reject</Button>
              <Button disabled={!data.actionable || busy || selected.length === 0}
                      onClick={() => setReturnOpen(true)}>
                Return {selected.length > 0 ? `${selected.length} day(s)` : 'days'}
              </Button>
              <Button type="primary" disabled={!data.actionable || busy}
                      loading={busy}
                      onClick={() => act(() => timesheetApprovalApi.approve(data.timesheetId),
                                         'Timesheet approved')}>
                Approve month
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {!data.actionable && data.notActionableReason && (
        <Alert type="info" showIcon message={data.notActionableReason} />
      )}
      {returnedDays > 0 && (
        <Alert
          type="warning"
          showIcon
          message={`${returnedDays} day(s) are with the employee for correction`}
          description="The month cannot be approved until they are fixed and resubmitted."
        />
      )}
      {data.findings.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message="Notes the employee's submission carried"
          description={
            <ul style={{ margin: '6px 0 0 16px', padding: 0 }}>
              {data.findings.map((f, i) => <li key={i}>{f.message}</li>)}
            </ul>
          }
        />
      )}
      {data.employeeComment && (
        <Alert type="info" showIcon message="Employee comment" description={data.employeeComment} />
      )}

      <Row gutter={16}>
        <Col xs={8}>
          <Card><Statistic title="Entered" value={data.totalEnteredHours} suffix="h" /></Card>
        </Col>
        <Col xs={8}>
          <Card><Statistic title="Attendance" value={data.totalAttendanceHours} suffix="h" /></Card>
        </Col>
        <Col xs={8}>
          <Card>
            <Statistic
              title="Variance"
              value={data.totalVarianceHours}
              suffix="h"
              valueStyle={{ color: Math.abs(data.totalVarianceHours) > 1 ? '#d46b08' : undefined }}
            />
          </Card>
        </Col>
      </Row>

      <Card title="Days" extra={
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          Tick the days to send back, then choose Return
        </Typography.Text>
      }>
        <Table
          rowKey="dayId"
          size="small"
          dataSource={data.days}
          columns={columns}
          pagination={false}
          scroll={{ x: 1100 }}
          rowSelection={{
            selectedRowKeys: selected,
            onChange: (keys) => setSelected(keys as string[]),
          }}
        />
      </Card>

      {Object.keys(data.totals).length > 0 && (
        <Card title="Monthly quantities">
          <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} size="small" bordered>
            {Object.entries(data.totals).map(([code, qty]) => (
              <Descriptions.Item key={code} label={code.replaceAll('_', ' ').toLowerCase()}>
                {qty}
              </Descriptions.Item>
            ))}
          </Descriptions>
          <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 12 }}>
            These quantities are what payroll will price once the period is locked.
          </Typography.Text>
        </Card>
      )}

      <Modal
        open={returnOpen}
        title={`Return ${selected.length} day(s) for correction`}
        okText="Return to employee"
        okButtonProps={{ disabled: !reason.trim(), loading: busy }}
        onOk={() => act(
          () => timesheetApprovalApi.returnDays(
            data.timesheetId,
            data.days.filter((d) => selected.includes(d.dayId)).map((d) => d.date),
            reason,
          ),
          'Days returned to the employee',
        )}
        onCancel={() => setReturnOpen(false)}
      >
        <Typography.Paragraph type="secondary">
          Only the days you ticked reopen. Everything else stays approved, so the
          employee fixes what you flagged and nothing more.
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          placeholder="What should the employee change? e.g. 13 Jan overtime should be 2h not 4h"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </Modal>

      <Modal
        open={rejectOpen}
        title="Reject this submission"
        okText="Reject"
        okButtonProps={{ danger: true, disabled: !reason.trim(), loading: busy }}
        onOk={() => act(() => timesheetApprovalApi.reject(data.timesheetId, reason),
                        'Timesheet rejected')}
        onCancel={() => setRejectOpen(false)}
      >
        <Typography.Paragraph type="secondary">
          Rejecting sends the whole month back as a draft and clears every day's
          verdict. To fix specific days instead, close this and use Return.
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          placeholder="Why is this submission being rejected?"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </Modal>
    </Space>
  )
}

export default TimesheetReviewPage
