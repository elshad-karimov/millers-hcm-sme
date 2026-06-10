import { useEffect, useState } from 'react'
import { App as AntdApp, Card, Checkbox, List, Space, Tag, Typography } from 'antd'
import { selfApi, type SelfChecklistTask } from '../api/self'

const { Text } = Typography

/**
 * M266 — Phase F.* "✅ Checklist tasks" widget.
 *
 * <p>Closes the 7th and last ProfileItemType (CHECKLIST_ITEM) for
 * self-service visibility. When HR adds a CHECKLIST_ITEM to a
 * position profile, the M250 auto-grant creates a PENDING grant
 * row on hire; the employee sees it here and ticks it off when
 * done, which calls the same {@code markActive} backend path as
 * the operator's "mark active" button.
 *
 * <p>Renders nothing when the list is empty so the dashboard
 * stays clean for employees with no outstanding tasks.
 */
export function ChecklistTasksWidget() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<SelfChecklistTask[] | null>(null)
  const [completing, setCompleting] = useState<Record<string, boolean>>({})

  useEffect(() => {
    selfApi
      .checklistTasks()
      .then(setRows)
      .catch(() => setRows([]))
  }, [])

  if (rows == null || rows.length === 0) return null

  const complete = async (id: string) => {
    setCompleting((m) => ({ ...m, [id]: true }))
    try {
      await selfApi.completeChecklistTask(id)
      // Remove the row in place — the backend marked it ACTIVE so
      // it will no longer come back from /checklist-tasks anyway.
      setRows((current) => (current ?? []).filter((r) => r.id !== id))
      message.success('Task completed')
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Failed to mark complete')
    } finally {
      setCompleting((m) => {
        const { [id]: _, ...rest } = m
        return rest
      })
    }
  }

  return (
    <Card
      size="small"
      title={
        <Space>
          <span>✅ Onboarding checklist</span>
          <Tag color="cyan">{rows.length}</Tag>
        </Space>
      }
      style={{ marginBottom: 16 }}
    >
      <List
        size="small"
        dataSource={rows}
        renderItem={(t) => (
          <List.Item>
            <Checkbox
              disabled={!!completing[t.id]}
              onChange={(e) => {
                if (e.target.checked) complete(t.id)
              }}
            >
              <Space direction="vertical" size={0}>
                <Text strong>{t.label}</Text>
                {t.notes && (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {t.notes}
                  </Text>
                )}
              </Space>
            </Checkbox>
          </List.Item>
        )}
      />
    </Card>
  )
}
