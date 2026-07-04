// HCM_16 M413 — succession planning (PRD §16.2/§16.3/§16.4).
// Critical positions + succession plans + approval workflow. HR-only.

import { useEffect, useState } from 'react'
import { App as AntdApp, Button, Card, Table, Tabs, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title } = Typography

interface CriticalPosition {
  id: string
  positionId: string
  criticalityReason?: string
  replacementDifficulty?: string
  vacancyRisk?: string
  successionRequired: boolean
  active: boolean
}

interface SuccessionPlan {
  id: string
  criticalPositionId: string
  planOwnerEmployeeId: string
  effectiveDate: string
  reviewDate?: string
  status: string
  emergencySuccessorEmployeeId?: string
  notes?: string
}

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  SUBMITTED: 'blue',
  APPROVED: 'green',
  ACTIVE: 'cyan',
  ARCHIVED: 'gray',
}

export function SuccessionPlansPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [critPositions, setCritPositions] = useState<CriticalPosition[]>([])
  const [plans, setPlans] = useState<SuccessionPlan[]>([])
  const [loading, setLoading] = useState(true)

  const load = () => {
    setLoading(true)
    Promise.all([
      api.get<CriticalPosition[]>('/succession/critical-positions'),
      api.get<SuccessionPlan[]>('/succession/plans'),
    ])
      .then(([c, p]) => {
        setCritPositions(c.data)
        setPlans(p.data)
      })
      .catch(() => message.error('Failed to load succession planning'))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line react-hooks/exhaustive-deps

  const critColumns: ColumnsType<CriticalPosition> = [
    { title: 'Position ID', dataIndex: 'positionId', width: 200 },
    { title: 'Criticality Reason', dataIndex: 'criticalityReason', ellipsis: true },
    { title: 'Replacement', dataIndex: 'replacementDifficulty', width: 120 },
    { title: 'Vacancy Risk', dataIndex: 'vacancyRisk', width: 120 },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v) => (v ? <Tag color="green">Yes</Tag> : <Tag>No</Tag>),
    },
  ]

  const planColumns: ColumnsType<SuccessionPlan> = [
    { title: 'Critical Position', dataIndex: 'criticalPositionId', width: 200 },
    { title: 'Plan Owner', dataIndex: 'planOwnerEmployeeId', width: 200 },
    { title: 'Effective Date', dataIndex: 'effectiveDate', width: 120 },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Actions',
      width: 100,
      render: (_, plan) =>
        canWrite &&
        plan.status === 'DRAFT' && (
          <Button
            size="small"
            type="primary"
            onClick={() => {
              api
                .post(`/succession/plans/${plan.id}/submit`)
                .then(() => {
                  message.success('Plan submitted')
                  load()
                })
                .catch(() => message.error('Failed to submit'))
            }}
          >
            Submit
          </Button>
        ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Title level={2}>Succession Planning</Title>
      <Tabs
        items={[
          {
            key: 'crit',
            label: 'Critical Positions',
            children: (
              <Card>
                <Table
                  dataSource={critPositions}
                  columns={critColumns}
                  rowKey="id"
                  loading={loading}
                  pagination={{ pageSize: 20 }}
                />
              </Card>
            ),
          },
          {
            key: 'plans',
            label: 'Succession Plans',
            children: (
              <Card>
                <Table
                  dataSource={plans}
                  columns={planColumns}
                  rowKey="id"
                  loading={loading}
                  pagination={{ pageSize: 20 }}
                />
              </Card>
            ),
          },
        ]}
      />
    </div>
  )
}
