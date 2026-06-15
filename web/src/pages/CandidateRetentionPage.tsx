import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Alert,
  Button,
  Card,
  Empty,
  Popconfirm,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import type { ColumnsType } from 'antd/es/table'
import { recruitmentApi, type RetentionReport, type RetentionRow } from '../api/recruitment'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

/**
 * M294 — Recruitment PRD §46/§47: candidate consent retention. Lists
 * candidates past (and approaching) the retention window; HR anonymises
 * them individually or sweeps all due records.
 */
export function CandidateRetentionPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const { hasRole } = useAuth()
  const canErase = hasRole(...RoleSets.HR_ADMIN_WRITE)
  const [report, setReport] = useState<RetentionReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    recruitmentApi
      .retentionReport()
      .then(setReport)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load retention report'))
      .finally(() => setLoading(false))
  }, [message])

  useEffect(load, [load])

  const anonymize = async (row: RetentionRow) => {
    setBusy(true)
    try {
      await recruitmentApi.anonymizeCandidate(row.id, 'manual erasure')
      message.success(`Anonymised ${row.candidateNo}`)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed',
      )
    } finally {
      setBusy(false)
    }
  }

  const sweep = async () => {
    setBusy(true)
    try {
      const r = await recruitmentApi.retentionSweep(false)
      message.success(`Anonymised ${r.count} candidate(s)`)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed',
      )
    } finally {
      setBusy(false)
    }
  }

  if (loading || !report) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin />
      </div>
    )
  }

  const dueCols: ColumnsType<RetentionRow> = [
    {
      title: '#',
      dataIndex: 'candidateNo',
      width: 120,
      render: (no: string, r) => <Link to={`/recruitment/candidates/${r.id}/edit`}>{no}</Link>,
    },
    { title: 'Name', dataIndex: 'fullName', ellipsis: true },
    { title: 'Email', dataIndex: 'email', render: (v?: string) => v ?? '—' },
    {
      title: 'Retention date',
      dataIndex: 'retentionDate',
      width: 130,
      render: (d: string) => new Date(d).toLocaleDateString(),
    },
    {
      title: 'Overdue by',
      dataIndex: 'daysUntilDue',
      width: 110,
      render: (d: number) => <Tag color="red">{Math.abs(d)}d</Tag>,
    },
    ...(canErase
      ? [
          {
            title: '',
            width: 120,
            render: (_: unknown, r: RetentionRow) => (
              <Popconfirm
                title="Anonymise this candidate? PII is scrubbed irreversibly."
                onConfirm={() => anonymize(r)}
              >
                <Button size="small" danger loading={busy}>
                  Anonymise
                </Button>
              </Popconfirm>
            ),
          },
        ]
      : []),
  ]

  const upcomingCols: ColumnsType<RetentionRow> = [
    {
      title: '#',
      dataIndex: 'candidateNo',
      width: 120,
      render: (no: string, r) => <Link to={`/recruitment/candidates/${r.id}/edit`}>{no}</Link>,
    },
    { title: 'Name', dataIndex: 'fullName', ellipsis: true },
    {
      title: 'Retention date',
      dataIndex: 'retentionDate',
      width: 130,
      render: (d: string) => new Date(d).toLocaleDateString(),
    },
    {
      title: 'Due in',
      dataIndex: 'daysUntilDue',
      width: 100,
      render: (d: number) => <Tag color="gold">{d}d</Tag>,
    },
  ]

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          Candidate retention
        </Typography.Title>
      }
      extra={
        <Space>
          <Button onClick={load}>Refresh</Button>
          {canErase && report.dueCount > 0 && (
            <Popconfirm
              title={`Anonymise all ${report.dueCount} candidate(s) past the window?`}
              onConfirm={sweep}
            >
              <Button type="primary" danger loading={busy}>
                Anonymise all due
              </Button>
            </Popconfirm>
          )}
          <Button onClick={() => navigate('/recruitment/candidates')}>Back to candidates</Button>
        </Space>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          message={`Retention window: ${report.retentionMonths} months after the most recent consent / contact / created date. Hired candidates and those with a live application are kept. Anonymising scrubs PII but keeps the record for funnel analytics.`}
        />
        <Space size="large">
          <Statistic title="Due now" value={report.dueCount} valueStyle={{ color: report.dueCount > 0 ? '#f5222d' : undefined }} />
          <Statistic title="Approaching" value={report.upcomingCount} valueStyle={{ color: report.upcomingCount > 0 ? '#fa8c16' : undefined }} />
        </Space>

        <Card size="small" type="inner" title="Due for anonymisation">
          <Table<RetentionRow>
            rowKey="id"
            size="small"
            columns={dueCols}
            dataSource={report.due}
            pagination={{ pageSize: 25 }}
            locale={{ emptyText: <Empty description="Nothing past the retention window" /> }}
          />
        </Card>

        <Card size="small" type="inner" title="Approaching the window">
          <Table<RetentionRow>
            rowKey="id"
            size="small"
            columns={upcomingCols}
            dataSource={report.upcoming}
            pagination={{ pageSize: 25 }}
            locale={{ emptyText: <Empty description="Nothing approaching" /> }}
          />
        </Card>
      </Space>
    </Card>
  )
}
