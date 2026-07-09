// M476 — Attrition risk analysis (HR_ADMIN only).

import { useEffect, useState } from 'react'
import { Button, Card, Table, Tag, App as AntdApp } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { attritionRiskApi, type AttritionRisk } from '../../api/analytics'

export function AttritionRiskPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<AttritionRisk[]>([])
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    attritionRiskApi
      .listAll()
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleRecompute = async () => {
    try {
      await attritionRiskApi.recompute()
      message.success('Recompute started')
      setTimeout(() => load(), 2000)
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Recompute failed')
    }
  }

  const columns: ColumnsType<AttritionRisk> = [
    { title: 'Employee ID', dataIndex: 'employeeId', width: 280 },
    {
      title: 'Score',
      dataIndex: 'score',
      width: 100,
      sorter: (a, b) => b.score - a.score,
      render: (score: number) => (
        <Tag color={score >= 70 ? 'red' : score >= 40 ? 'orange' : 'green'}>
          {score}
        </Tag>
      ),
    },
    { title: 'Factors', dataIndex: 'factors', ellipsis: true },
    { title: 'Computed At', dataIndex: 'computedAt', width: 180 },
  ]

  return (
    <Card title="Attrition Risk" extra={<Button onClick={handleRecompute}>Recompute</Button>}>
      <Table rowKey="id" columns={columns} dataSource={rows} loading={loading} />
    </Card>
  )
}
