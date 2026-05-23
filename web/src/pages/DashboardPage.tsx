import React, { useEffect, useState } from 'react'
import { Button, Card, Col, Row, Skeleton, Space, Tag, Typography } from 'antd'
import {
  ApartmentOutlined,
  ArrowDownOutlined,
  ArrowUpOutlined,
  BarChartOutlined,
  BankOutlined,
  BellOutlined,
  BookOutlined,
  CalendarOutlined,
  ClockCircleOutlined,
  CoffeeOutlined,
  FileDoneOutlined,
  IdcardOutlined,
  InboxOutlined,
  MinusOutlined,
  RightOutlined,
  RocketOutlined,
  SolutionOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserAddOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { brand } from '../theme'
import { employeesApi } from '../api/employees'
import { workflowApi } from '../api/workflow'
import { leaveApi } from '../api/leave'
import { recruitmentApi } from '../api/recruitment'
import { payrollApi } from '../api/payroll'

const { Text, Title } = Typography

// ── Helpers ─────────────────────────────────────────────────────────────────

function greetingForHour(h: number): string {
  if (h < 12) return 'Good morning'
  if (h < 18) return 'Good afternoon'
  return 'Good evening'
}

function capitalise(s: string) {
  return s.charAt(0).toUpperCase() + s.slice(1)
}

const MONTH_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

// ── Sparkline (decorative SVG area chart) ───────────────────────────────────

function Sparkline({ id, data, color }: { id: string; data: number[]; color: string }) {
  const H = 44
  const W = 88
  const max = Math.max(...data)
  const min = Math.min(...data)
  const rng = max - min || 1
  const pad = H * 0.1
  const pts = data.map((v, i) => ({
    x: (i / (data.length - 1)) * W,
    y: H - pad - ((v - min) / rng) * (H - 2 * pad),
  }))
  const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')
  const area = `${line} L${W},${H} L0,${H} Z`
  const gradId = `spk-${id}`

  return (
    <svg width={W} height={H} style={{ display: 'block', overflow: 'visible' }}>
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.25" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradId})`} />
      <path d={line} fill="none" stroke={color} strokeWidth="2.5"
        strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

// ── Pending action card ──────────────────────────────────────────────────────

interface PendingCardProps {
  icon: React.ReactNode
  iconColor: string
  iconBg: string
  label: string
  count: string | number
  subLabel: string
  actionLabel: string
  to: string
}

function PendingCard({ icon, iconColor, iconBg, label, count, subLabel, actionLabel, to }: PendingCardProps) {
  const navigate = useNavigate()
  return (
    <Card
      style={{
        borderRadius: 16,
        border: '1px solid rgba(0,0,0,0.06)',
        boxShadow: '0 2px 12px rgba(0,0,0,0.04)',
        height: '100%',
      }}
      styles={{ body: { padding: 20 } }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14, height: '100%' }}>
        {/* Icon + count row */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div
            style={{
              width: 48, height: 48, borderRadius: 14,
              background: iconBg, color: iconColor,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 22, flexShrink: 0,
            }}
          >
            {icon}
          </div>
          <div>
            <Text style={{ fontSize: 12, color: '#888', display: 'block', lineHeight: 1.3, textTransform: 'uppercase', letterSpacing: '0.4px', fontWeight: 500 }}>
              {label}
            </Text>
            <Text style={{ fontSize: 26, fontWeight: 700, color: brand.ink, lineHeight: 1.15, display: 'block' }}>
              {count}
            </Text>
          </div>
        </div>

        {/* Sub-label */}
        <Text style={{ fontSize: 13, color: '#888', lineHeight: 1.4 }}>{subLabel}</Text>

        {/* Action button */}
        <Button
          type="primary"
          size="small"
          style={{
            background: brand.purple, borderColor: brand.purple,
            borderRadius: 8, width: '100%', fontWeight: 500,
            marginTop: 'auto',
          }}
          onClick={() => navigate(to)}
        >
          {actionLabel}
        </Button>
      </div>
    </Card>
  )
}

// ── Shortcut tiles ───────────────────────────────────────────────────────────

const SHORTCUTS = [
  { key: 'employees',    to: '/employees',             label: 'Employees',      icon: <IdcardOutlined />,       tint: 'rgba(91,63,229,0.10)',   iconColor: brand.purpleDeep },
  { key: 'organization', to: '/organization',          label: 'Organization',   icon: <ApartmentOutlined />,    tint: 'rgba(63,191,191,0.15)', iconColor: brand.cyanDeep   },
  { key: 'positions',    to: '/positions',             label: 'Positions',      icon: <SolutionOutlined />,     tint: 'rgba(157,238,58,0.18)',  iconColor: brand.greenDeep  },
  { key: 'timesheets',   to: '/timesheets',            label: 'Timesheets',     icon: <FileDoneOutlined />,     tint: 'rgba(91,63,229,0.08)',   iconColor: brand.purple     },
  { key: 'leave',        to: '/leave/requests',        label: 'Leave Requests', icon: <CoffeeOutlined />,       tint: 'rgba(255,159,64,0.15)', iconColor: '#D46B08'        },
  { key: 'payroll',      to: '/payroll/runs',          label: 'Payroll',        icon: <BankOutlined />,         tint: 'rgba(157,238,58,0.18)',  iconColor: brand.greenDeep  },
  { key: 'recruitment',  to: '/recruitment/vacancies', label: 'Recruitment',    icon: <UserAddOutlined />,      tint: 'rgba(91,63,229,0.10)',   iconColor: brand.purpleDeep },
  { key: 'performance',  to: '/performance/reviews',   label: 'Performance',    icon: <RocketOutlined />,       tint: 'rgba(63,191,191,0.15)', iconColor: brand.cyanDeep   },
  { key: 'learning',     to: '/learning/courses',      label: 'Learning',       icon: <BookOutlined />,         tint: 'rgba(157,238,58,0.18)',  iconColor: brand.greenDeep  },
  { key: 'reports',      to: '/reports',               label: 'Reports',        icon: <BarChartOutlined />,     tint: 'rgba(255,159,64,0.15)', iconColor: '#D46B08'        },
  { key: 'approvals',    to: '/inbox',                 label: 'Approvals',      icon: <InboxOutlined />,        tint: 'rgba(91,63,229,0.10)',   iconColor: brand.purpleDeep },
  { key: 'my',           to: '/my',                    label: 'My Workspace',   icon: <UserOutlined />,         tint: 'rgba(63,191,191,0.15)', iconColor: brand.cyanDeep   },
]

// ── Static activity feed ─────────────────────────────────────────────────────

const ACTIVITIES = [
  {
    icon: <CoffeeOutlined />, color: '#D46B08', bg: 'rgba(255,159,64,0.13)',
    text: 'Leave request submitted by Aliya Aliyeva', time: '2 hours ago',
  },
  {
    icon: <FileDoneOutlined />, color: brand.purpleDeep, bg: 'rgba(91,63,229,0.10)',
    text: '3 timesheets are awaiting your approval', time: '3 hours ago',
  },
  {
    icon: <UserAddOutlined />, color: brand.cyanDeep, bg: 'rgba(63,191,191,0.15)',
    text: 'Onboarding started for new hire: Alex Chen', time: 'Yesterday, 16:20',
  },
  {
    icon: <BankOutlined />, color: brand.greenDeep, bg: 'rgba(157,238,58,0.18)',
    text: 'Payroll draft for May 2026 has been created', time: 'Yesterday, 09:00',
  },
  {
    icon: <UserOutlined />, color: '#999', bg: 'rgba(0,0,0,0.05)',
    text: 'Rashad Aliyev updated their profile information', time: '2 days ago',
  },
]

// ── Dashboard state ──────────────────────────────────────────────────────────

interface DashStats {
  activeEmployees: number | null
  pendingApprovals: number | null
  onLeave: number | null
  openPositions: number | null
  pendingLeave: number | null
  pendingTimesheets: number | null
  payrollLabel: string
}

// ── Main component ───────────────────────────────────────────────────────────

export function DashboardPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const now = new Date()
  const greeting = greetingForHour(now.getHours())
  const displayName = user?.username ? capitalise(user.username) : 'Admin'
  const dateStr = now.toLocaleDateString('en-US', {
    weekday: 'short', month: 'long', day: 'numeric', year: 'numeric',
  })

  const [stats, setStats] = useState<DashStats>({
    activeEmployees: null,
    pendingApprovals: null,
    onLeave: null,
    openPositions: null,
    pendingLeave: null,
    pendingTimesheets: null,
    payrollLabel: '—',
  })
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    ;(async () => {
      const [empActive, empOnLeave, inboxRes, leaveRes, vacRes, runsRes] = await Promise.allSettled([
        employeesApi.list({ status: 'ACTIVE', page: 0, size: 1 }),
        employeesApi.list({ status: 'ON_LEAVE', page: 0, size: 1 }),
        workflowApi.inbox(),
        leaveApi.requests({ status: 'PENDING', page: 0, size: 1 }),
        recruitmentApi.vacancies({ status: 'OPEN', page: 0, size: 1 }),
        payrollApi.runs(),
      ])
      if (!alive) return

      const inbox = inboxRes.status === 'fulfilled' ? inboxRes.value : []
      const tsCount = inbox.filter(
        (i) =>
          i.definitionCode?.toUpperCase().includes('TIMESHEET') ||
          i.subjectModule?.toUpperCase().includes('TIMESHEET'),
      ).length

      const nextRun =
        runsRes.status === 'fulfilled'
          ? runsRes.value.find(
              (r) => r.status === 'DRAFT' || r.status === 'CALCULATED' || r.status === 'UNDER_REVIEW',
            )
          : undefined

      setStats({
        activeEmployees: empActive.status === 'fulfilled' ? empActive.value.totalElements : null,
        onLeave: empOnLeave.status === 'fulfilled' ? empOnLeave.value.totalElements : null,
        pendingApprovals: inbox.length,
        openPositions: vacRes.status === 'fulfilled' ? vacRes.value.totalElements : null,
        pendingLeave: leaveRes.status === 'fulfilled' ? leaveRes.value.totalElements : null,
        pendingTimesheets: tsCount,
        payrollLabel: nextRun
          ? `${MONTH_SHORT[nextRun.periodMonth - 1]} ${nextRun.periodYear}`
          : 'No active run',
      })
      setLoading(false)
    })()
    return () => { alive = false }
  }, [])

  // KPI card definitions (sparkline data is illustrative / decorative)
  const kpiCards = [
    {
      id: 'emp',
      label: 'Active Employees',
      value: stats.activeEmployees,
      trend: 'up' as const,
      trendLabel: '+3 this month',
      icon: <TeamOutlined />,
      bg: 'rgba(91,63,229,0.10)',
      iconColor: brand.purple,
      sparkColor: brand.purple,
      sparkData: [220, 225, 228, 231, 235, 241, 248],
    },
    {
      id: 'approvals',
      label: 'Pending Approvals',
      value: stats.pendingApprovals,
      trend: 'down' as const,
      trendLabel: '-4 since yesterday',
      icon: <InboxOutlined />,
      bg: 'rgba(255,159,64,0.15)',
      iconColor: '#D46B08',
      sparkColor: '#FA8C16',
      sparkData: [20, 18, 16, 19, 15, 14, 12],
    },
    {
      id: 'leave',
      label: 'Employees on Leave',
      value: stats.onLeave,
      trend: 'flat' as const,
      trendLabel: 'Same as last week',
      icon: <CoffeeOutlined />,
      bg: 'rgba(63,191,191,0.15)',
      iconColor: brand.cyanDeep,
      sparkColor: brand.cyan,
      sparkData: [4, 6, 5, 7, 5, 6, 5],
    },
    {
      id: 'positions',
      label: 'Open Positions',
      value: stats.openPositions,
      trend: 'up' as const,
      trendLabel: '+1 this week',
      icon: <SolutionOutlined />,
      bg: 'rgba(157,238,58,0.18)',
      iconColor: brand.greenDeep,
      sparkColor: brand.green,
      sparkData: [1, 2, 2, 3, 2, 3, 3],
    },
  ]

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div style={{ padding: '24px 28px', maxWidth: 1440, margin: '0 auto' }}>

      {/* ── Greeting row ──────────────────────────────────────────────────── */}
      <Row align="middle" justify="space-between" wrap={false} style={{ marginBottom: 28, gap: 16 }}>
        <Col flex="auto" style={{ minWidth: 0 }}>
          <Title level={2} style={{ margin: 0, color: brand.ink, fontWeight: 700, fontSize: 26, whiteSpace: 'nowrap' }}>
            {greeting}, {displayName}
          </Title>
          <Text style={{ color: '#888', fontSize: 14 }}>
            Here is what needs your attention today.
          </Text>
        </Col>
        <Col flex="none">
          <Space size={10} wrap>
            {/* Date chip */}
            <Tag
              icon={<CalendarOutlined style={{ color: brand.purpleDeep }} />}
              style={{
                borderRadius: 20, padding: '5px 14px', fontSize: 13,
                background: '#fff', border: '1px solid rgba(0,0,0,0.10)',
                color: brand.ink, fontWeight: 500, lineHeight: '20px',
              }}
            >
              {dateStr}
            </Tag>

            {/* Notifications chip */}
            <Tag
              icon={<BellOutlined style={{ color: brand.purple }} />}
              style={{
                borderRadius: 20, padding: '5px 14px', fontSize: 13,
                background: '#fff', border: '1px solid rgba(0,0,0,0.10)',
                color: brand.ink, cursor: 'pointer', lineHeight: '20px',
              }}
              onClick={() => navigate('/inbox')}
            >
              {stats.pendingApprovals !== null ? `${stats.pendingApprovals} new` : '— new'}
            </Tag>

            {/* Tasks chip */}
            <Tag
              icon={<ClockCircleOutlined style={{ color: brand.purpleDeep }} />}
              style={{
                borderRadius: 20, padding: '5px 14px', fontSize: 13,
                background: '#fff', border: '1px solid rgba(0,0,0,0.10)',
                color: brand.ink, lineHeight: '20px',
              }}
            >
              {stats.pendingLeave !== null ? `${stats.pendingLeave} due today` : '— due today'}
            </Tag>

            {/* Quick action */}
            <Button
              type="primary"
              icon={<ThunderboltOutlined />}
              style={{
                background: brand.purple, borderColor: brand.purple,
                borderRadius: 20, fontWeight: 600, paddingLeft: 20, paddingRight: 20,
                height: 34,
              }}
              onClick={() => navigate('/employees')}
            >
              Quick action
            </Button>
          </Space>
        </Col>
      </Row>

      {/* ── KPI cards ─────────────────────────────────────────────────────── */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        {kpiCards.map((kpi) => (
          <Col key={kpi.id} xs={24} sm={12} md={6}>
            <Card
              style={{
                borderRadius: 18,
                border: '1px solid rgba(0,0,0,0.06)',
                boxShadow: '0 2px 16px rgba(0,0,0,0.05)',
                overflow: 'hidden',
              }}
              styles={{ body: { padding: 20 } }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                {/* Left: icon + number + trend */}
                <div>
                  <div
                    style={{
                      width: 48, height: 48, borderRadius: 14,
                      background: kpi.bg, color: kpi.iconColor,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: 22, marginBottom: 12,
                    }}
                  >
                    {kpi.icon}
                  </div>
                  <Text style={{ fontSize: 12, color: '#999', display: 'block', textTransform: 'uppercase', letterSpacing: '0.4px', fontWeight: 500 }}>
                    {kpi.label}
                  </Text>
                  {loading ? (
                    <Skeleton.Input active size="small" style={{ width: 60, height: 36, marginTop: 6, borderRadius: 6 }} />
                  ) : (
                    <Text style={{ fontSize: 36, fontWeight: 700, color: brand.ink, lineHeight: 1.15, display: 'block', marginTop: 2 }}>
                      {kpi.value ?? '—'}
                    </Text>
                  )}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 6 }}>
                    {kpi.trend === 'up' && <ArrowUpOutlined style={{ color: '#52C41A', fontSize: 11 }} />}
                    {kpi.trend === 'down' && <ArrowDownOutlined style={{ color: '#FF4D4F', fontSize: 11 }} />}
                    {kpi.trend === 'flat' && <MinusOutlined style={{ color: '#aaa', fontSize: 11 }} />}
                    <Text style={{
                      fontSize: 12,
                      color: kpi.trend === 'up' ? '#52C41A' : kpi.trend === 'down' ? '#FF4D4F' : '#aaa',
                    }}>
                      {kpi.trendLabel}
                    </Text>
                  </div>
                </div>

                {/* Right: sparkline */}
                <div style={{ marginTop: 4, opacity: 0.85 }}>
                  <Sparkline id={kpi.id} data={kpi.sparkData} color={kpi.sparkColor} />
                </div>
              </div>
            </Card>
          </Col>
        ))}
      </Row>

      {/* ── Two-column main section ────────────────────────────────────────── */}
      <Row gutter={[20, 20]}>

        {/* Left column — Pending actions + My shortcuts */}
        <Col xs={24} xl={17}>

          {/* Pending Actions */}
          <div style={{ marginBottom: 24 }}>
            <Title level={5} style={{ margin: '0 0 14px', color: brand.ink, fontWeight: 600, fontSize: 15 }}>
              Pending Actions
            </Title>
            <Row gutter={[14, 14]}>
              <Col xs={24} sm={12}>
                <PendingCard
                  icon={<CoffeeOutlined />}
                  iconColor="#D46B08"
                  iconBg="rgba(255,159,64,0.15)"
                  label="Leave Requests"
                  count={loading ? '…' : (stats.pendingLeave ?? '—')}
                  subLabel={`${stats.pendingLeave ?? '—'} pending approval`}
                  actionLabel="Review requests"
                  to="/leave/requests"
                />
              </Col>
              <Col xs={24} sm={12}>
                <PendingCard
                  icon={<FileDoneOutlined />}
                  iconColor={brand.purpleDeep}
                  iconBg="rgba(91,63,229,0.10)"
                  label="Timesheets"
                  count={loading ? '…' : (stats.pendingTimesheets ?? '—')}
                  subLabel="Need your approval"
                  actionLabel="Review timesheets"
                  to="/timesheets"
                />
              </Col>
              <Col xs={24} sm={12}>
                <PendingCard
                  icon={<UserAddOutlined />}
                  iconColor={brand.cyanDeep}
                  iconBg="rgba(63,191,191,0.15)"
                  label="Recruitment"
                  count={3}
                  subLabel="Interviews scheduled today"
                  actionLabel="View interviews"
                  to="/recruitment/vacancies"
                />
              </Col>
              <Col xs={24} sm={12}>
                <PendingCard
                  icon={<BankOutlined />}
                  iconColor={brand.greenDeep}
                  iconBg="rgba(157,238,58,0.18)"
                  label="Payroll"
                  count={loading ? '…' : stats.payrollLabel}
                  subLabel="Payroll run scheduled"
                  actionLabel="Go to payroll"
                  to="/payroll/runs"
                />
              </Col>
            </Row>
          </div>

          {/* My Shortcuts */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
              <Title level={5} style={{ margin: 0, color: brand.ink, fontWeight: 600, fontSize: 15 }}>
                My Shortcuts
              </Title>
              <Text style={{ fontSize: 13, color: brand.purple, cursor: 'pointer', fontWeight: 500 }}>
                Customize
              </Text>
            </div>
            <Row gutter={[10, 10]}>
              {SHORTCUTS.map((s) => (
                <Col key={s.key} xs={12} sm={8} md={6} lg={4}>
                  <Link to={s.to} style={{ textDecoration: 'none' }}>
                    <Card
                      hoverable
                      style={{
                        borderRadius: 14,
                        border: '1px solid rgba(0,0,0,0.06)',
                        transition: 'box-shadow 0.2s, transform 0.15s',
                      }}
                      styles={{ body: { padding: '14px 10px' } }}
                    >
                      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 9 }}>
                        <div
                          style={{
                            width: 38, height: 38, borderRadius: 10,
                            background: s.tint, color: s.iconColor,
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            fontSize: 18,
                          }}
                        >
                          {s.icon}
                        </div>
                        <Text style={{ fontSize: 12, color: brand.ink, textAlign: 'center', lineHeight: 1.3, fontWeight: 500 }}>
                          {s.label}
                        </Text>
                      </div>
                    </Card>
                  </Link>
                </Col>
              ))}
            </Row>
          </div>
        </Col>

        {/* Right column — Activity feed */}
        <Col xs={24} xl={7}>
          <Card
            style={{
              borderRadius: 18,
              border: '1px solid rgba(0,0,0,0.06)',
              boxShadow: '0 2px 16px rgba(0,0,0,0.05)',
              height: '100%',
            }}
            styles={{ body: { padding: '20px 20px 16px' } }}
          >
            <Title level={5} style={{ margin: '0 0 18px', color: brand.ink, fontWeight: 600, fontSize: 15 }}>
              Recent Activity
            </Title>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
              {ACTIVITIES.map((a, i) => (
                <div
                  key={i}
                  style={{
                    display: 'flex', gap: 12, alignItems: 'flex-start',
                    paddingBottom: 14,
                    marginBottom: i < ACTIVITIES.length - 1 ? 14 : 0,
                    borderBottom: i < ACTIVITIES.length - 1 ? '1px solid rgba(0,0,0,0.05)' : 'none',
                  }}
                >
                  <div
                    style={{
                      width: 36, height: 36, borderRadius: 10,
                      background: a.bg, color: a.color,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: 15, flexShrink: 0, marginTop: 1,
                    }}
                  >
                    {a.icon}
                  </div>
                  <div>
                    <Text style={{ fontSize: 13, color: brand.ink, lineHeight: 1.45, display: 'block' }}>
                      {a.text}
                    </Text>
                    <Text style={{ fontSize: 12, color: '#bbb', marginTop: 3, display: 'block' }}>
                      {a.time}
                    </Text>
                  </div>
                </div>
              ))}
            </div>

            <div style={{ textAlign: 'center', paddingTop: 12, borderTop: '1px solid rgba(0,0,0,0.05)' }}>
              <Link to="/inbox" style={{ fontSize: 13, color: brand.purple, fontWeight: 500 }}>
                Go to activity feed <RightOutlined style={{ fontSize: 10 }} />
              </Link>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
