import { useEffect, useState } from 'react'
import { Button, Card, Space, Typography } from 'antd'
import { ClockCircleOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { timesheetApprovalApi, type QueueRow } from '../api/timesheetApproval'

const { Text } = Typography

/**
 * Timesheets waiting for the signed-in person to approve.
 *
 * <p>Approving somebody's month is often not a manager's job here: an employee
 * can name any colleague as their timesheet approver, and the workflow routes
 * the month to that person. But the approval screen lives under Manager
 * Self-Service, a board a plain employee never sees — so the month arrived
 * with them and they had no way to know, and no way to reach it. It simply
 * stopped, and nobody could see where.
 *
 * <p>This is the missing doorway. It appears on My Workspace only when there
 * is something to act on, so it is a prompt rather than another empty card.
 */
export function TimesheetsAwaitingMeWidget() {
  const [rows, setRows] = useState<QueueRow[] | null>(null)

  useEffect(() => {
    const now = new Date()
    // This month and last: a month is usually approved just after it ends, so
    // the one people are chasing on the 3rd is the previous one.
    const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1)
    Promise.all([
      timesheetApprovalApi.queue(now.getFullYear(), now.getMonth() + 1).catch(() => [] as QueueRow[]),
      timesheetApprovalApi.queue(prev.getFullYear(), prev.getMonth() + 1).catch(() => [] as QueueRow[]),
    ])
      .then(([a, b]) => setRows([...a, ...b]))
      .catch(() => setRows([]))
  }, [])

  if (rows == null || rows.length === 0) return null

  const names = rows
    .map((r) => r.employeeName)
    .filter(Boolean)
    .slice(0, 3)
    .join(', ')

  return (
    <Card
      size="small"
      title={
        <Space>
          <ClockCircleOutlined />
          <span>Timesheets waiting for you ({rows.length})</span>
        </Space>
      }
      extra={
        <Link to="/manager/timesheets">
          <Button size="small" type="primary" ghost>
            Review
          </Button>
        </Link>
      }
    >
      <Text>
        {names}
        {rows.length > 3 ? ` and ${rows.length - 3} more` : ''}
      </Text>
      <br />
      <Text type="secondary">
        Nobody is paid for a month until its timesheet is approved.
      </Text>
    </Card>
  )
}
