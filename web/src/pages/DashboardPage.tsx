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
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/AuthContext'
import { brand } from '../theme'
import { employeesApi } from '../api/employees'
import { workflowApi } from '../api/workflow'
import { leaveApi } from '../api/leave'
import { recruitmentApi } from '../api/recruitment'
import { recruitmentAnalyticsApi, type StaleSummary } from '../api/recruitmentAnalytics'
import { pathAssignmentsApi, type PathBacklogSummary } from '../api/learningPaths'
import { payrollApi } from '../api/payroll'

const { Text, Title } = Typography

// ── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Pure-static greeting-key resolver. Returns the i18n key under
 * `dashboard.greeting`; the caller looks it up via t() so the same
 * decision logic is reused regardless of locale.
 */
function greetingKeyForHour(h: number): 'morning' | 'afternoon' | 'evening' {
  if (h < 12) return 'morning'
  if (h < 18) return 'afternoon'
  return 'evening'
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
//
// Label is i18n-keyed (`dashboard.shortcuts.labels.<key>`) not stored
// here. Adding a new shortcut = drop a row below + add one key in
// dashboard.json (az + en); zero copy-paste of strings.

const SHORTCUTS = [
  { key: 'employees',    to: '/employees',             icon: <IdcardOutlined />,    tint: 'rgba(91,63,229,0.10)',  iconColor: brand.purpleDeep },
  { key: 'organization', to: '/organization',          icon: <ApartmentOutlined />, tint: 'rgba(63,191,191,0.15)', iconColor: brand.cyanDeep   },
  { key: 'positions',    to: '/positions',             icon: <SolutionOutlined />,  tint: 'rgba(157,238,58,0.18)', iconColor: brand.greenDeep  },
  { key: 'timesheets',   to: '/timesheets',            icon: <FileDoneOutlined />,  tint: 'rgba(91,63,229,0.08)',  iconColor: brand.purple     },
  { key: 'leave',        to: '/leave/requests',        icon: <CoffeeOutlined />,    tint: 'rgba(255,159,64,0.15)', iconColor: '#D46B08'        },
  { key: 'payroll',      to: '/payroll/runs',          icon: <BankOutlined />,      tint: 'rgba(157,238,58,0.18)', iconColor: brand.greenDeep  },
  { key: 'recruitment',  to: '/recruitment/vacancies', icon: <UserAddOutlined />,   tint: 'rgba(91,63,229,0.10)',  iconColor: brand.purpleDeep },
  { key: 'performance',  to: '/performance/reviews',   icon: <RocketOutlined />,    tint: 'rgba(63,191,191,0.15)', iconColor: brand.cyanDeep   },
  { key: 'learning',     to: '/learning/courses',      icon: <BookOutlined />,      tint: 'rgba(157,238,58,0.18)', iconColor: brand.greenDeep  },
  { key: 'reports',      to: '/reports',               icon: <BarChartOutlined />,  tint: 'rgba(255,159,64,0.15)', iconColor: '#D46B08'        },
  { key: 'approvals',    to: '/inbox',                 icon: <InboxOutlined />,     tint: 'rgba(91,63,229,0.10)',  iconColor: brand.purpleDeep },
  { key: 'my',           to: '/my',                    icon: <UserOutlined />,      tint: 'rgba(63,191,191,0.15)', iconColor: brand.cyanDeep   },
] as const

// ── Static activity feed ─────────────────────────────────────────────────────
//
// Each row references an i18n key under `dashboard.activity.items.*`
// plus interpolation parameters. The visible strings live in the JSON
// locale files; if M80's live activity feed lands later, the data
// source changes but the per-row mapper stays the same.

type ActivityRow = {
  icon: React.ReactNode
  color: string
  bg: string
  textKey: string
  textVars: Record<string, string | number>
  timeKey: string
  timeVars?: Record<string, string | number>
}

const ACTIVITIES: ActivityRow[] = [
  {
    icon: <CoffeeOutlined />, color: '#D46B08', bg: 'rgba(255,159,64,0.13)',
    textKey: 'items.leaveSubmitted', textVars: { name: 'Aliya Aliyeva' },
    timeKey: 'time.hoursAgo', timeVars: { count: 2 },
  },
  {
    icon: <FileDoneOutlined />, color: brand.purpleDeep, bg: 'rgba(91,63,229,0.10)',
    textKey: 'items.timesheetsAwaiting', textVars: { count: 3 },
    timeKey: 'time.hoursAgo', timeVars: { count: 3 },
  },
  {
    icon: <UserAddOutlined />, color: brand.cyanDeep, bg: 'rgba(63,191,191,0.15)',
    textKey: 'items.onboardingStarted', textVars: { name: 'Alex Chen' },
    timeKey: 'time.yesterday', timeVars: { time: '16:20' },
  },
  {
    icon: <BankOutlined />, color: brand.greenDeep, bg: 'rgba(157,238,58,0.18)',
    textKey: 'items.payrollDraft', textVars: { month: 'May', year: 2026 },
    timeKey: 'time.yesterday', timeVars: { time: '09:00' },
  },
  {
    icon: <UserOutlined />, color: '#999', bg: 'rgba(0,0,0,0.05)',
    textKey: 'items.profileUpdated', textVars: { name: 'Rashad Aliyev' },
    timeKey: 'time.daysAgo', timeVars: { count: 2 },
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
  staleCandidates: StaleSummary | null
  pathBacklog: PathBacklogSummary | null
}

// ── Main component ───────────────────────────────────────────────────────────

export function DashboardPage() {
  const { user, hasRole } = useAuth()
  const showRecruiterTiles = hasRole(
    'SYSTEM_ADMIN', 'HR_ADMIN', 'HR_SPECIALIST', 'RECRUITER',
  )
  // M99 — HR-only tile. Same role envelope the backend endpoint requires.
  const showLearningTiles = hasRole(
    'SYSTEM_ADMIN', 'HR_ADMIN', 'HR_SPECIALIST', 'AUDITOR',
  )
  const navigate = useNavigate()
  // M229 — dashboard namespace, plus i18n.language for the dateStr
  // formatter so the Sat/Şən and month names follow the active locale.
  const { t, i18n } = useTranslation('dashboard')
  const now = new Date()
  const greeting = t(`greeting.${greetingKeyForHour(now.getHours())}`)
  const displayName = user?.username ? capitalise(user.username) : t('greeting.defaultName')
  const dateLocale = i18n.language?.startsWith('az') ? 'az-AZ' : 'en-US'
  const dateStr = now.toLocaleDateString(dateLocale, {
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
    staleCandidates: null,
    pathBacklog: null,
  })
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    ;(async () => {
      const [empActive, empOnLeave, inboxRes, leaveRes, vacRes, runsRes, staleRes, backlogRes] =
        await Promise.allSettled([
          employeesApi.list({ status: 'ACTIVE', page: 0, size: 1 }),
          employeesApi.list({ status: 'ON_LEAVE', page: 0, size: 1 }),
          workflowApi.inbox(),
          leaveApi.requests({ status: 'PENDING', page: 0, size: 1 }),
          recruitmentApi.vacancies({ status: 'OPEN', page: 0, size: 1 }),
          payrollApi.runs(),
          showRecruiterTiles
            ? recruitmentAnalyticsApi.staleSummary(30)
            : Promise.resolve(null),
          showLearningTiles
            ? pathAssignmentsApi.backlogSummary()
            : Promise.resolve(null),
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
          : t('pendingActions.payroll.noActiveRun'),
        staleCandidates:
          staleRes.status === 'fulfilled' && staleRes.value
            ? (staleRes.value as StaleSummary)
            : null,
        pathBacklog:
          backlogRes.status === 'fulfilled' && backlogRes.value
            ? (backlogRes.value as PathBacklogSummary)
            : null,
      })
      setLoading(false)
    })()
    return () => { alive = false }
  }, [])

  // KPI card definitions (sparkline data is illustrative / decorative)
  const kpiCards = [
    {
      id: 'emp',
      label: t('kpi.activeEmployees'),
      value: stats.activeEmployees,
      trend: 'up' as const,
      trendLabel: t('kpi.trend.upMonth', { count: 3 }),
      icon: <TeamOutlined />,
      bg: 'rgba(91,63,229,0.10)',
      iconColor: brand.purple,
      sparkColor: brand.purple,
      sparkData: [220, 225, 228, 231, 235, 241, 248],
    },
    {
      id: 'approvals',
      label: t('kpi.pendingApprovals'),
      value: stats.pendingApprovals,
      trend: 'down' as const,
      trendLabel: t('kpi.trend.downYesterday', { count: 4 }),
      icon: <InboxOutlined />,
      bg: 'rgba(255,159,64,0.15)',
      iconColor: '#D46B08',
      sparkColor: '#FA8C16',
      sparkData: [20, 18, 16, 19, 15, 14, 12],
    },
    {
      id: 'leave',
      label: t('kpi.employeesOnLeave'),
      value: stats.onLeave,
      trend: 'flat' as const,
      trendLabel: t('kpi.trend.flatWeek'),
      icon: <CoffeeOutlined />,
      bg: 'rgba(63,191,191,0.15)',
      iconColor: brand.cyanDeep,
      sparkColor: brand.cyan,
      sparkData: [4, 6, 5, 7, 5, 6, 5],
    },
    {
      id: 'positions',
      label: t('kpi.openPositions'),
      value: stats.openPositions,
      trend: 'up' as const,
      trendLabel: t('kpi.trend.upWeek', { count: 1 }),
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
            {t('subtitle')}
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
              {stats.pendingApprovals !== null
                ? t('chip.newCount', { count: stats.pendingApprovals })
                : t('chip.newEmpty')}
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
              {stats.pendingLeave !== null
                ? t('chip.dueTodayCount', { count: stats.pendingLeave })
                : t('chip.dueTodayEmpty')}
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
              {t('quickAction')}
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
              {t('pendingActions.heading')}
            </Title>
            <Row gutter={[14, 14]}>
              <Col xs={24} sm={12}>
                <PendingCard
                  icon={<CoffeeOutlined />}
                  iconColor="#D46B08"
                  iconBg="rgba(255,159,64,0.15)"
                  label={t('pendingActions.leave.label')}
                  count={loading ? '…' : (stats.pendingLeave ?? '—')}
                  subLabel={t('pendingActions.leave.sub', { count: stats.pendingLeave ?? 0 })}
                  actionLabel={t('pendingActions.leave.action')}
                  to="/leave/requests"
                />
              </Col>
              <Col xs={24} sm={12}>
                <PendingCard
                  icon={<FileDoneOutlined />}
                  iconColor={brand.purpleDeep}
                  iconBg="rgba(91,63,229,0.10)"
                  label={t('pendingActions.timesheets.label')}
                  count={loading ? '…' : (stats.pendingTimesheets ?? '—')}
                  subLabel={t('pendingActions.timesheets.sub')}
                  actionLabel={t('pendingActions.timesheets.action')}
                  to="/timesheets"
                />
              </Col>
              <Col xs={24} sm={12}>
                {showRecruiterTiles ? (
                  <PendingCard
                    icon={<UserAddOutlined />}
                    iconColor={
                      (stats.staleCandidates?.bucket90plus ?? 0) > 0
                        ? '#cf1322'
                        : brand.cyanDeep
                    }
                    iconBg="rgba(63,191,191,0.15)"
                    label={t('pendingActions.staleCandidates.label')}
                    count={
                      loading
                        ? '…'
                        : (stats.staleCandidates?.total ?? '—')
                    }
                    subLabel={
                      stats.staleCandidates
                        ? t('pendingActions.staleCandidates.subSummary', {
                            p90: stats.staleCandidates.bucket90plus,
                            p60: stats.staleCandidates.bucket60to89,
                            p30: stats.staleCandidates.bucket30to59,
                          })
                        : t('pendingActions.staleCandidates.subEmpty')
                    }
                    actionLabel={t('pendingActions.staleCandidates.action')}
                    to="/recruitment/analytics"
                  />
                ) : (
                  <PendingCard
                    icon={<UserAddOutlined />}
                    iconColor={brand.cyanDeep}
                    iconBg="rgba(63,191,191,0.15)"
                    label={t('pendingActions.recruitment.label')}
                    count={loading ? '…' : (stats.openPositions ?? '—')}
                    subLabel={t('pendingActions.recruitment.sub')}
                    actionLabel={t('pendingActions.recruitment.action')}
                    to="/recruitment/vacancies"
                  />
                )}
              </Col>
              <Col xs={24} sm={12}>
                <PendingCard
                  icon={<BankOutlined />}
                  iconColor={brand.greenDeep}
                  iconBg="rgba(157,238,58,0.18)"
                  label={t('pendingActions.payroll.label')}
                  count={loading ? '…' : stats.payrollLabel}
                  subLabel={t('pendingActions.payroll.sub')}
                  actionLabel={t('pendingActions.payroll.action')}
                  to="/payroll/runs"
                />
              </Col>
              {/* M99 — HR-only path completion tile. Mirrors the M89 stale
                  tile pattern: count = total active, sub-label breaks it
                  into urgency buckets, red icon when overdue. */}
              {showLearningTiles && (
                <Col xs={24} sm={12}>
                  <PendingCard
                    icon={<BookOutlined />}
                    iconColor={
                      (stats.pathBacklog?.overdue ?? 0) > 0
                        ? '#cf1322'
                        : brand.purpleDeep
                    }
                    iconBg="rgba(91,63,229,0.10)"
                    label={t('pendingActions.learningPaths.label')}
                    count={loading ? '…' : (stats.pathBacklog?.active ?? '—')}
                    subLabel={
                      stats.pathBacklog
                        ? t('pendingActions.learningPaths.subSummary', {
                            overdue: stats.pathBacklog.overdue,
                            due7: stats.pathBacklog.dueWithin7,
                            due30: stats.pathBacklog.dueWithin30,
                          })
                        : t('pendingActions.learningPaths.subEmpty')
                    }
                    actionLabel={t('pendingActions.learningPaths.action')}
                    to="/learning/paths"
                  />
                </Col>
              )}
            </Row>
          </div>

          {/* My Shortcuts */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
              <Title level={5} style={{ margin: 0, color: brand.ink, fontWeight: 600, fontSize: 15 }}>
                {t('shortcuts.heading')}
              </Title>
              <Text style={{ fontSize: 13, color: brand.purple, cursor: 'pointer', fontWeight: 500 }}>
                {t('shortcuts.customize')}
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
                          {t(`shortcuts.labels.${s.key}`)}
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
              {t('activity.heading')}
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
                      {t(a.textKey, a.textVars)}
                    </Text>
                    <Text style={{ fontSize: 12, color: '#bbb', marginTop: 3, display: 'block' }}>
                      {t(a.timeKey, a.timeVars)}
                    </Text>
                  </div>
                </div>
              ))}
            </div>

            <div style={{ textAlign: 'center', paddingTop: 12, borderTop: '1px solid rgba(0,0,0,0.05)' }}>
              <Link to="/inbox" style={{ fontSize: 13, color: brand.purple, fontWeight: 500 }}>
                {t('activity.goTo')} <RightOutlined style={{ fontSize: 10 }} />
              </Link>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
