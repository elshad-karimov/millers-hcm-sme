import { useEffect, useState } from 'react'
import { Alert, Card, List, Space, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { selfApi, type SelfRequiredDocument } from '../api/self'

const { Text } = Typography

/**
 * M263 — Phase F.5a "📂 Documents owed to HR" widget.
 *
 * <p>Embedded on the My Workspace dashboard so an employee immediately
 * sees which documents HR is waiting for. Driven by the M262 auto-grant
 * — every REQUIRED_DOCUMENT profile item on the position creates one
 * row here for the new hire.
 *
 * <p>Renders nothing if the list is empty so we don't clutter the
 * dashboard with a "no documents needed" placeholder for the typical
 * case.
 */
export function RequiredDocumentsWidget() {
  const [rows, setRows] = useState<SelfRequiredDocument[] | null>(null)

  useEffect(() => {
    selfApi
      .requiredDocuments()
      .then(setRows)
      .catch(() => setRows([]))
  }, [])

  if (rows == null || rows.length === 0) return null

  const overdue = rows.filter(
    (r) => r.requiredByDate && dayjs(r.requiredByDate).isBefore(dayjs(), 'day'),
  )

  return (
    <Card
      size="small"
      title={
        <Space>
          <span>📂 Documents owed to HR</span>
          <Tag color="red">{rows.length}</Tag>
          {overdue.length > 0 && <Tag color="volcano">⚠ {overdue.length} overdue</Tag>}
        </Space>
      }
      style={{ marginBottom: 16 }}
    >
      <Alert
        type={overdue.length > 0 ? 'error' : 'warning'}
        showIcon
        message={
          overdue.length > 0
            ? 'You have overdue documents. Please upload them as soon as possible.'
            : 'HR is waiting for these documents. Upload them through the standard channel.'
        }
        style={{ marginBottom: 12 }}
      />
      <List
        size="small"
        dataSource={rows}
        renderItem={(d) => {
          const isOverdue =
            d.requiredByDate && dayjs(d.requiredByDate).isBefore(dayjs(), 'day')
          return (
            <List.Item>
              <List.Item.Meta
                title={
                  <Space>
                    <Text strong>{d.label}</Text>
                    <Tag>{d.documentType.replace(/_/g, ' ')}</Tag>
                  </Space>
                }
                description={
                  d.requiredByDate ? (
                    <Text
                      type={isOverdue ? 'danger' : 'secondary'}
                      style={{ fontSize: 12 }}
                    >
                      {isOverdue ? '⚠ Was due ' : 'Due by '}
                      {d.requiredByDate}
                    </Text>
                  ) : (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      No due date set
                    </Text>
                  )
                }
              />
            </List.Item>
          )
        }}
      />
    </Card>
  )
}
