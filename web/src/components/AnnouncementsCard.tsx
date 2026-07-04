import { useEffect, useState } from 'react'
import { Alert, Space, App as AntdApp } from 'antd'
import { announcementsApi, type Announcement } from '../api/announcements'

/**
 * M430 — Announcements card on MyWorkspace dashboard.
 * Shows active announcements (audience-filtered). Client-side dismissible.
 */
export function AnnouncementsCard() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [announcements, setAnnouncements] = useState<Announcement[]>([])
  const [dismissed, setDismissed] = useState<Set<string>>(new Set())

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      try {
        const res = await announcementsApi.active()
        setAnnouncements(res.data)
      } catch (err: any) {
        message.error('Failed to load announcements: ' + (err.message || ''))
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const visible = announcements.filter((a) => !dismissed.has(a.id))

  if (!loading && visible.length === 0) {
    return null
  }

  return (
    <Space direction="vertical" size="small" style={{ width: '100%' }}>
      {visible.map((ann) => (
        <Alert
          key={ann.id}
          type="info"
          showIcon
          closable
          message={<strong>{ann.title}</strong>}
          description={ann.body}
          onClose={() => setDismissed((prev) => new Set(prev).add(ann.id))}
        />
      ))}
    </Space>
  )
}
