// M117 — Per-employee change-history panel.
//
// Mounted as a tab on the EmployeeDetailPage. Aggregates effective-dated
// employment-history slices + status slices + audit_log JSON diffs into
// a single chronological feed. Distinct from the M76 lifecycle timeline
// (which tracks "things that happened to" the employee — leave requests,
// disciplinary actions, etc.).

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Col,
  Drawer,
  Empty,
  Row,
  Space,
  Spin,
  Tag,
  Timeline,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import {
  changeHistoryApi,
  type ChangeEvent,
  type ChangeEventCategory,
  type EmployeeChangeHistory,
} from '../api/changeHistory'

const { Text, Paragraph } = Typography

const CATEGORY_COLOR: Record<ChangeEventCategory, string> = {
  EMPLOYMENT_CHANGE: 'blue',
  STATUS_CHANGE: 'gold',
  AUDIT: 'purple',
}

const CATEGORY_LABEL: Record<ChangeEventCategory, string> = {
  EMPLOYMENT_CHANGE: 'Employment',
  STATUS_CHANGE: 'Status',
  AUDIT: 'Audit',
}

function pretty(s?: string | null): string {
  if (!s) return ''
  try { return JSON.stringify(JSON.parse(s), null, 2) }
  catch { return s }
}

export function ChangeHistoryPanel({ employeeId }: { employeeId: string }) {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<EmployeeChangeHistory | null>(null)
  const [loading, setLoading] = useState(true)
  const [detail, setDetail] = useState<ChangeEvent | null>(null)

  useEffect(() => {
    setLoading(true)
    changeHistoryApi
      .forEmployee(employeeId)
      .then(setData)
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load change history'),
      )
      .finally(() => setLoading(false))
  }, [employeeId, message])

  if (loading) return <Spin />
  if (!data || data.events.length === 0) {
    return <Empty description="No field changes on record yet." />
  }

  return (
    <>
      <Paragraph type="secondary" style={{ fontSize: 12 }}>
        Field-level history of changes to this employee's record. Combines effective-dated
        employment slices, status transitions, and direct audit-log entries. Click any row
        to see the before/after JSON.
      </Paragraph>

      <Timeline
        items={data.events.map((event) => ({
          key: event.rowId,
          color: CATEGORY_COLOR[event.category],
          children: (
            <Space direction="vertical" size={2} style={{ width: '100%' }}>
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Space size="small">
                  <Tag color={CATEGORY_COLOR[event.category]}>
                    {CATEGORY_LABEL[event.category]}
                  </Tag>
                  <Text strong>{event.title}</Text>
                </Space>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {dayjs(event.eventTime).format('YYYY-MM-DD HH:mm')}
                </Text>
              </Space>
              {event.effectiveDate && event.effectiveDate !== event.eventTime.slice(0, 10) && (
                <Text type="secondary" style={{ fontSize: 11 }}>
                  Effective from {event.effectiveDate}
                </Text>
              )}
              {event.summary && (
                <Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap', fontSize: 12 }}>
                  {event.summary}
                </Paragraph>
              )}
              <Space size="small" style={{ fontSize: 11 }}>
                {event.actor && (
                  <Text type="secondary">
                    by <Text code style={{ fontSize: 11 }}>{event.actor}</Text>
                  </Text>
                )}
                {(event.oldValue || event.newValue) && (
                  <a onClick={() => setDetail(event)}>view diff</a>
                )}
              </Space>
            </Space>
          ),
        }))}
      />

      <Drawer
        open={!!detail}
        onClose={() => setDetail(null)}
        width={780}
        title={detail ? `${CATEGORY_LABEL[detail.category]} · ${detail.title}` : ''}
      >
        {detail && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Row gutter={12}>
              <Col span={8}>
                <Text type="secondary" style={{ fontSize: 11 }}>WHEN</Text>
                <div>{dayjs(detail.eventTime).format('YYYY-MM-DD HH:mm:ss')}</div>
              </Col>
              <Col span={8}>
                <Text type="secondary" style={{ fontSize: 11 }}>ACTOR</Text>
                <div><Text strong>{detail.actor ?? '—'}</Text></div>
              </Col>
              <Col span={8}>
                <Text type="secondary" style={{ fontSize: 11 }}>SOURCE</Text>
                <div>
                  {detail.sourceModule
                    ? `${detail.sourceModule} · ${detail.sourceEntity ?? ''}`
                    : '—'}
                </div>
              </Col>
            </Row>
            <Row gutter={12}>
              <Col xs={24} md={12}>
                <Text type="secondary" style={{ fontSize: 11 }}>BEFORE</Text>
                {detail.oldValue ? (
                  <pre style={{
                    margin: 0, fontSize: 11, maxHeight: 500, overflow: 'auto',
                    background: '#fafafa', padding: 8, borderRadius: 4,
                    whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                  }}>{pretty(detail.oldValue)}</pre>
                ) : (
                  <Text type="secondary">— (null — likely a CREATE)</Text>
                )}
              </Col>
              <Col xs={24} md={12}>
                <Text type="secondary" style={{ fontSize: 11 }}>AFTER</Text>
                {detail.newValue ? (
                  <pre style={{
                    margin: 0, fontSize: 11, maxHeight: 500, overflow: 'auto',
                    background: '#f6ffed', padding: 8, borderRadius: 4,
                    whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                  }}>{pretty(detail.newValue)}</pre>
                ) : (
                  <Text type="secondary">— (null — likely a DELETE)</Text>
                )}
              </Col>
            </Row>
          </Space>
        )}
      </Drawer>
    </>
  )
}
