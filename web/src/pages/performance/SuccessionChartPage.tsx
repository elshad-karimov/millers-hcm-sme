// HCM_16 M415 — Replacement chart: critical positions + incumbents + successors + coverage.
// Shows readiness, risk, linked dev plans. HR can attach dev plans to nominations.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Empty,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../../api/client'
import { READINESS_COLOR, READINESS_LABEL } from '../../api/successionNominations'

const { Title, Text } = Typography

type Readiness = 'READY_NOW' | 'READY_SOON' | 'READY_LONG_TERM' | 'UNDER_DEVELOPMENT'

interface ReplacementChartResponse {
  positions: PositionEntry[]
  coverage: CoverageSummary
}

interface CoverageSummary {
  noSuccessor: number
  oneReady: number
  multipleReady: number
}

interface PositionEntry {
  criticalPositionId: string
  positionId: string
  positionCode: string
  positionTitle: string
  vacancyRisk: string
  replacementDifficulty: string
  incumbents: EmployeeInfo[]
  successors: SuccessorInfo[]
}

interface EmployeeInfo {
  employeeId: string
  name: string
  employeeNo: string
}

interface SuccessorInfo {
  nominationId: string
  employeeId: string
  name: string
  readiness: Readiness
  riskOfLoss?: string | null
  impactOfLoss?: string | null
  devPlanIds: string[]
}

interface DevPlan {
  id: string
  employeeId: string
  planName: string
  status: string
}

const RISK_COLOR = { LOW: 'green', MEDIUM: 'orange', HIGH: 'red', CRITICAL: 'volcano' }

export function SuccessionChartPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(true)
  const [data, setData] = useState<ReplacementChartResponse | null>(null)
  const [attachOpen, setAttachOpen] = useState(false)
  const [selectedNomination, setSelectedNomination] = useState<string | null>(null)
  const [devPlans, setDevPlans] = useState<DevPlan[]>([])
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null)
  const [attaching, setAttaching] = useState(false)

  const load = () => {
    setLoading(true)
    api
      .get<ReplacementChartResponse>('/succession/replacement-chart')
      .then((r) => setData(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load chart'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openAttach = (nominationId: string, employeeId: string) => {
    setSelectedNomination(nominationId)
    setAttachOpen(true)
    // Load dev plans for this employee
    api
      .get<DevPlan[]>('/performance/dev-plans', { params: { employeeId } })
      .then((r) => setDevPlans(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load dev plans'))
  }

  const submitAttach = async () => {
    if (!selectedNomination || !selectedPlanId) return
    setAttaching(true)
    try {
      await api.post(`/succession/nominations/${selectedNomination}/dev-plans`, {
        devPlanId: selectedPlanId,
      })
      message.success('Dev plan attached')
      setAttachOpen(false)
      setSelectedPlanId(null)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Attach failed',
      )
    } finally {
      setAttaching(false)
    }
  }

  const detachPlan = async (nominationId: string, planId: string) => {
    try {
      await api.delete(`/succession/nominations/${nominationId}/dev-plans/${planId}`)
      message.success('Dev plan detached')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Detach failed',
      )
    }
  }

  if (loading || !data) {
    return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />
  }

  const successorCols: ColumnsType<SuccessorInfo> = [
    {
      title: 'Successor',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.name ?? '—'}</Text>
        </Space>
      ),
    },
    {
      title: 'Readiness',
      dataIndex: 'readiness',
      width: 140,
      render: (t: Readiness) => <Tag color={READINESS_COLOR[t]}>{READINESS_LABEL[t]}</Tag>,
    },
    {
      title: 'Risk',
      dataIndex: 'riskOfLoss',
      width: 100,
      render: (v: string) =>
        v ? <Tag color={RISK_COLOR[v as keyof typeof RISK_COLOR]}>{v}</Tag> : '—',
    },
    {
      title: 'Impact',
      dataIndex: 'impactOfLoss',
      width: 100,
      render: (v: string) =>
        v ? <Tag color={RISK_COLOR[v as keyof typeof RISK_COLOR]}>{v}</Tag> : '—',
    },
    {
      title: 'Dev Plans',
      dataIndex: 'devPlanIds',
      render: (ids: string[], r) => (
        <Space wrap>
          {ids.length === 0 && '—'}
          {ids.map((id) => (
            <Tag
              key={id}
              closable
              onClose={(e) => {
                e.preventDefault()
                detachPlan(r.nominationId, id)
              }}
            >
              {id.slice(0, 8)}…
            </Tag>
          ))}
          <Button size="small" type="link" onClick={() => openAttach(r.nominationId, r.employeeId)}>
            + Attach
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3}>Replacement Chart</Title>

      <Row gutter={16}>
        <Col span={8}>
          <Card>
            <Statistic
              title="No Successor"
              value={data.coverage.noSuccessor}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="One Ready"
              value={data.coverage.oneReady}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="Multiple Ready"
              value={data.coverage.multipleReady}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
      </Row>

      {data.positions.length === 0 ? (
        <Empty description="No active critical positions" />
      ) : (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {data.positions.map((pos) => (
            <Card
              key={pos.criticalPositionId}
              title={
                <Space>
                  <Text strong>
                    {pos.positionCode} — {pos.positionTitle}
                  </Text>
                  <Tag color="blue">{pos.vacancyRisk} risk</Tag>
                  <Tag>{pos.replacementDifficulty} difficulty</Tag>
                </Space>
              }
              size="small"
            >
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Text type="secondary">
                  <strong>Incumbents:</strong>{' '}
                  {pos.incumbents.length === 0
                    ? 'None (vacant)'
                    : pos.incumbents.map((e) => `${e.name} (${e.employeeNo})`).join(', ')}
                </Text>
                <Table
                  rowKey="nominationId"
                  columns={successorCols}
                  dataSource={pos.successors}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: <Empty description="No successors nominated yet" /> }}
                />
              </Space>
            </Card>
          ))}
        </Space>
      )}

      <Modal
        open={attachOpen}
        title="Attach development plan"
        onCancel={() => setAttachOpen(false)}
        onOk={submitAttach}
        confirmLoading={attaching}
        okText="Attach"
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text>Select a development plan for this successor:</Text>
          <Select
            showSearch
            style={{ width: '100%' }}
            placeholder="Pick a dev plan"
            value={selectedPlanId}
            onChange={setSelectedPlanId}
            options={devPlans.map((p) => ({
              value: p.id,
              label: `${p.planName} (${p.status})`,
            }))}
          />
        </Space>
      </Modal>
    </Space>
  )
}
