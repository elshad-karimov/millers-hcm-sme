// HCM_20 M427 — Budget variance dashboard.

import { useEffect, useState } from 'react'
import { App as AntdApp, Card, Select, Space, Statistic, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../api/client'
import type { BudgetCycle } from '../api/budgeting'
import { budgetApi } from '../api/budgeting'

interface DepartmentVariance {
  orgUnitId: string
  budgetAmount: number
  actualCost: number
  variance: number
  utilizationPct: number
  status: 'UNDER' | 'WARNING' | 'OVER'
}

const STATUS_COLOR: Record<DepartmentVariance['status'], string> = {
  UNDER: 'green',
  WARNING: 'orange',
  OVER: 'red',
}

export function VarianceDashboardPage() {
  const { message } = AntdApp.useApp()
  const [cycles, setCycles] = useState<BudgetCycle[]>([])
  const [selectedCycle, setSelectedCycle] = useState<string | null>(null)
  const [variances, setVariances] = useState<DepartmentVariance[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    budgetApi.listCycles('OPEN').then((c) => {
      setCycles(c)
      if (c.length > 0) setSelectedCycle(c[0].id)
    }).catch(() => setCycles([]))
  }, [])

  useEffect(() => {
    if (!selectedCycle) return
    setLoading(true)
    api
      .get<DepartmentVariance[]>('/budgets/variance', { params: { cycleId: selectedCycle } })
      .then((r) => setVariances(r.data))
      .catch((err) => message.error(err?.response?.data?.message ?? 'Could not load variance'))
      .finally(() => setLoading(false))
  }, [selectedCycle])

  const totals = variances.reduce(
    (acc, v) => ({
      budget: acc.budget + Number(v.budgetAmount),
      actual: acc.actual + Number(v.actualCost),
      variance: acc.variance + Number(v.variance),
    }),
    { budget: 0, actual: 0, variance: 0 },
  )

  const columns: ColumnsType<DepartmentVariance> = [
    { title: 'Org Unit ID', dataIndex: 'orgUnitId', width: 280, ellipsis: true },
    {
      title: 'Budget',
      dataIndex: 'budgetAmount',
      align: 'right' as const,
      width: 130,
      render: (v: number) => Number(v).toLocaleString(),
    },
    {
      title: 'Actual',
      dataIndex: 'actualCost',
      align: 'right' as const,
      width: 130,
      render: (v: number) => Number(v).toLocaleString(),
    },
    {
      title: 'Variance',
      dataIndex: 'variance',
      align: 'right' as const,
      width: 130,
      render: (v: number) => Number(v).toLocaleString(),
    },
    {
      title: 'Utilization %',
      dataIndex: 'utilizationPct',
      align: 'right' as const,
      width: 120,
      render: (v: number) => `${Number(v).toFixed(2)}%`,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (v: DepartmentVariance['status']) => <Tag color={STATUS_COLOR[v]}>{v}</Tag>,
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card size="small">
        <Space>
          <label>Budget Cycle:</label>
          <Select
            style={{ width: 300 }}
            value={selectedCycle}
            onChange={setSelectedCycle}
            options={cycles.map((c) => ({ value: c.id, label: `${c.name} (${c.code})` }))}
          />
        </Space>
      </Card>

      {selectedCycle && (
        <>
          <Card size="small">
            <Space size="large" wrap>
              <Statistic title="Total budget" value={totals.budget} precision={0} />
              <Statistic title="Total actual" value={totals.actual} precision={0} />
              <Statistic
                title="Total variance"
                value={totals.variance}
                precision={0}
                valueStyle={{ color: totals.variance < 0 ? '#cf1322' : '#3f8600' }}
              />
            </Space>
          </Card>

          <Card title="Department Variance">
            <Table
              size="small"
              rowKey="orgUnitId"
              columns={columns}
              dataSource={variances}
              loading={loading}
              pagination={false}
            />
          </Card>
        </>
      )}
    </Space>
  )
}
