import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Alert,
  Button,
  Card,
  Empty,
  Popconfirm,
  Radio,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { recruitmentApi, type DuplicateCandidate, type DuplicateGroup } from '../api/recruitment'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

/**
 * M293 — Recruitment PRD §12: candidate duplicate detection + merge.
 * Lists groups of candidates that look like the same person; HR picks the
 * surviving master and merges the rest into it.
 */
const MATCH_COLOR: Record<DuplicateGroup['matchType'], string> = {
  EMAIL: 'red',
  PHONE: 'orange',
  NAME: 'blue',
}

export function CandidateDuplicatesPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const { hasRole } = useAuth()
  const canMerge = hasRole(...RoleSets.HR_ADMIN_WRITE)
  const [groups, setGroups] = useState<DuplicateGroup[]>([])
  const [loading, setLoading] = useState(true)
  const [merging, setMerging] = useState(false)
  // Per-group chosen primary (candidate id).
  const [primaryOf, setPrimaryOf] = useState<Record<string, string>>({})

  const load = useCallback(() => {
    setLoading(true)
    recruitmentApi
      .candidateDuplicates()
      .then((g) => {
        setGroups(g)
        // Default each group's primary to its oldest candidate.
        const defaults: Record<string, string> = {}
        g.forEach((grp, i) => {
          const oldest = [...grp.candidates].sort((a, b) =>
            a.createdAt.localeCompare(b.createdAt),
          )[0]
          if (oldest) defaults[`${grp.matchType}:${grp.matchValue}:${i}`] = oldest.id
        })
        setPrimaryOf(defaults)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to scan'))
      .finally(() => setLoading(false))
  }, [message])

  useEffect(load, [load])

  const mergeGroup = async (group: DuplicateGroup, key: string) => {
    const primaryId = primaryOf[key]
    if (!primaryId) {
      message.warning('Pick a master record first')
      return
    }
    const others = group.candidates.filter((c) => c.id !== primaryId)
    setMerging(true)
    try {
      for (const dup of others) {
        await recruitmentApi.mergeCandidates(primaryId, dup.id)
      }
      message.success(`Merged ${others.length} duplicate(s) into the master`)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Merge failed',
      )
    } finally {
      setMerging(false)
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin />
      </div>
    )
  }

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          Duplicate candidates
        </Typography.Title>
      }
      extra={
        <Space>
          <Button onClick={load}>Re-scan</Button>
          <Button onClick={() => navigate('/recruitment/candidates')}>Back to candidates</Button>
        </Space>
      }
    >
      {groups.length === 0 ? (
        <Empty description="No duplicate candidates detected" />
      ) : (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Alert
            type="info"
            message="Pick the master record in each group, then merge. Applications, notes, tags, education, experience, skills and documents move to the master; the duplicate is retired (kept for audit)."
          />
          {groups.map((group, i) => {
            const key = `${group.matchType}:${group.matchValue}:${i}`
            return (
              <Card
                key={key}
                size="small"
                type="inner"
                title={
                  <Space>
                    <Tag color={MATCH_COLOR[group.matchType]}>{group.matchType}</Tag>
                    <span>{group.matchValue}</span>
                    <Typography.Text type="secondary">
                      {group.candidates.length} candidates
                    </Typography.Text>
                  </Space>
                }
                extra={
                  canMerge && (
                    <Popconfirm
                      title="Merge the others into the selected master?"
                      onConfirm={() => mergeGroup(group, key)}
                    >
                      <Button type="primary" size="small" loading={merging}>
                        Merge into master
                      </Button>
                    </Popconfirm>
                  )
                }
              >
                <Radio.Group
                  value={primaryOf[key]}
                  onChange={(e) => setPrimaryOf((m) => ({ ...m, [key]: e.target.value }))}
                  style={{ width: '100%' }}
                >
                  <Table<DuplicateCandidate>
                    rowKey="id"
                    size="small"
                    dataSource={group.candidates}
                    pagination={false}
                    columns={[
                      {
                        title: 'Master',
                        width: 70,
                        render: (_: unknown, c) => <Radio value={c.id} />,
                      },
                      {
                        title: '#',
                        dataIndex: 'candidateNo',
                        width: 120,
                        render: (no: string, c) => (
                          <Link to={`/recruitment/candidates/${c.id}/edit`}>{no}</Link>
                        ),
                      },
                      { title: 'Name', dataIndex: 'fullName', ellipsis: true },
                      { title: 'Email', dataIndex: 'email', render: (v?: string) => v ?? '—' },
                      { title: 'Phone', dataIndex: 'phone', render: (v?: string) => v ?? '—' },
                      {
                        title: 'Apps',
                        dataIndex: 'applicationCount',
                        width: 70,
                      },
                      {
                        title: 'Created',
                        dataIndex: 'createdAt',
                        width: 110,
                        render: (d: string) => new Date(d).toLocaleDateString(),
                      },
                    ]}
                  />
                </Radio.Group>
              </Card>
            )
          })}
        </Space>
      )}
    </Card>
  )
}
