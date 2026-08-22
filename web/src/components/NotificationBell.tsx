import { useEffect, useRef, useState } from 'react'
import { Badge, Button, Dropdown, Empty, List, Space, Typography } from 'antd'
import { BellOutlined, CheckOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import {
  getUnreadCount,
  listNotifications,
  markAllRead,
  markRead,
  type NotificationItem,
} from '../api/notificationApi'
import { brand } from '../theme'

const { Text } = Typography

// ── Helpers ────────────────────────────────────────────────────────────────

function timeAgo(isoString: string): string {
  const diff = Date.now() - new Date(isoString).getTime()
  const mins = Math.floor(diff / 60_000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs}h ago`
  const days = Math.floor(hrs / 24)
  return `${days}d ago`
}

/** Map module name → a small coloured dot colour. */
function moduleColor(module: string | null): string {
  const map: Record<string, string> = {
    leave: brand.cyan,
    attendance: brand.green,
    payroll: brand.purple,
    performance: brand.purpleDeep,
    recruitment: brand.cyanDeep,
    timesheet: brand.greenDeep,
    lifecycle: '#e87a00',
    business_trip: '#e83a00',
    permission: '#b800e8',
  }
  if (!module) return '#999'
  const key = module.toLowerCase()
  return map[key] ?? brand.purple
}

/** Best-effort navigation target for a notification. */
function moduleRoute(module: string | null): string | null {
  if (!module) return null
  const map: Record<string, string> = {
    leave: '/leave/requests',
    attendance: '/attendance/summary',
    payroll: '/payroll/runs',
    performance: '/performance/reviews',
    recruitment: '/recruitment/vacancies',
    timesheet: '/my/timesheet',
    lifecycle: '/lifecycle/terminations',
    business_trip: '/business-trips',
    permission: '/permission/requests',
  }
  return map[module.toLowerCase()] ?? null
}

// ── Component ──────────────────────────────────────────────────────────────

/**
 * Notification bell icon with unread badge and dropdown inbox panel.
 * Polls the unread count every 60 seconds.
 */
export function NotificationBell({ color = brand.ink }: { color?: string } = {}) {
  const navigate = useNavigate()
  const [unread, setUnread] = useState(0)
  const [items, setItems] = useState<NotificationItem[]>([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // ── Fetch unread count ────────────────────────────────────────────────────

  const refreshCount = () => {
    getUnreadCount()
      .then((r) => setUnread(r.data.count))
      .catch(() => {/* silently ignore */})
  }

  useEffect(() => {
    refreshCount()
    intervalRef.current = setInterval(refreshCount, 60_000)
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [])

  // ── Fetch notifications when panel opens ──────────────────────────────────

  const loadNotifications = () => {
    setLoading(true)
    listNotifications({ page: 0, size: 10 })
      .then((r) => setItems(r.data.content))
      .catch(() => {/* silently ignore */})
      .finally(() => setLoading(false))
  }

  const handleOpenChange = (flag: boolean) => {
    setOpen(flag)
    if (flag) loadNotifications()
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  const handleMarkRead = async (item: NotificationItem) => {
    if (!item.readAt) {
      await markRead(item.id).catch(() => {})
      setItems((prev) =>
        prev.map((n) => (n.id === item.id ? { ...n, readAt: new Date().toISOString() } : n)),
      )
      setUnread((c) => Math.max(0, c - 1))
    }
    const route = moduleRoute(item.module)
    if (route) {
      setOpen(false)
      navigate(route)
    }
  }

  const handleMarkAllRead = async () => {
    await markAllRead().catch(() => {})
    setItems((prev) => prev.map((n) => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })))
    setUnread(0)
  }

  // ── Dropdown content ──────────────────────────────────────────────────────

  const dropdownContent = (
    <div
      style={{
        width: 380,
        background: '#fff',
        borderRadius: 8,
        boxShadow: brand.cardShadow,
        overflow: 'hidden',
      }}
    >
      {/* Header */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '10px 16px',
          borderBottom: '1px solid rgba(0,0,0,0.06)',
        }}
      >
        <Text strong style={{ color: brand.ink }}>
          Notifications
        </Text>
        {unread > 0 && (
          <Button
            type="link"
            size="small"
            icon={<CheckOutlined />}
            onClick={handleMarkAllRead}
            style={{ color: brand.purple, padding: 0 }}
          >
            Mark all read
          </Button>
        )}
      </div>

      {/* List */}
      {items.length === 0 && !loading ? (
        <Empty
          description="No notifications"
          style={{ padding: '24px 0' }}
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        />
      ) : (
        <List
          loading={loading}
          dataSource={items}
          renderItem={(item) => (
            <List.Item
              key={item.id}
              onClick={() => handleMarkRead(item)}
              style={{
                padding: '10px 16px',
                cursor: 'pointer',
                opacity: item.readAt ? 0.6 : 1,
                background: item.readAt ? 'transparent' : 'rgba(91,63,229,0.03)',
                borderBottom: '1px solid rgba(0,0,0,0.04)',
                transition: 'background 0.15s',
              }}
            >
              <Space align="start" style={{ width: '100%' }}>
                {/* Module colour dot */}
                <span
                  style={{
                    display: 'inline-block',
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: moduleColor(item.module),
                    marginTop: 6,
                    flexShrink: 0,
                  }}
                />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <Text
                    strong
                    style={{
                      display: 'block',
                      fontSize: 13,
                      color: brand.ink,
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                  >
                    {item.title}
                  </Text>
                  <Text
                    style={{
                      display: 'block',
                      fontSize: 12,
                      color: '#555',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                  >
                    {item.body}
                  </Text>
                  <Text style={{ fontSize: 11, color: '#999' }}>
                    {timeAgo(item.createdAt)}
                  </Text>
                </div>
              </Space>
            </List.Item>
          )}
        />
      )}
    </div>
  )

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <Dropdown
      open={open}
      onOpenChange={handleOpenChange}
      dropdownRender={() => dropdownContent}
      trigger={['click']}
      placement="bottomRight"
    >
      <Badge count={unread} size="small" offset={[-2, 2]}>
        <Button
          type="text"
          icon={<BellOutlined style={{ fontSize: 18, color }} />}
          style={{ display: 'flex', alignItems: 'center', padding: '0 8px' }}
        />
      </Badge>
    </Dropdown>
  )
}
