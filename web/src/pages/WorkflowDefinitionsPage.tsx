import { useEffect, useState } from 'react'
import { Card, Table, Tag, Typography, App as AntdApp } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../api/client'
import dayjs from 'dayjs'

interface WorkflowDefinitionResponse {
  id: string
  code: string
  name: string
  version: number
  effectiveFrom: string
  effectiveTo?: string
  active: boolean
  steps: any[]
}

export function WorkflowDefinitionsPage() {
  const { message } = AntdApp.useApp()
  const [definitions, setDefinitions] = useState<WorkflowDefinitionResponse[]>([])
  const [loading, setLoading] = useState(false)

  const fetchDefinitions = async () => {
    setLoading(true)
    try {
      const { data } = await api.get('/workflow/definitions')
      setDefinitions(data)
    } catch (err: any) {
      message.error(err.message || 'Failed to load workflow definitions')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchDefinitions()
  }, [])

  const columns: ColumnsType<WorkflowDefinitionResponse> = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      width: 200,
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'Version',
      dataIndex: 'version',
      key: 'version',
      width: 100,
      align: 'center',
    },
    {
      title: 'Effective From',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      width: 140,
      render: (val) => (val ? dayjs(val).format('YYYY-MM-DD') : '—'),
    },
    {
      title: 'Effective To',
      dataIndex: 'effectiveTo',
      key: 'effectiveTo',
      width: 140,
      render: (val) => (val ? dayjs(val).format('YYYY-MM-DD') : '—'),
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 100,
      render: (val: boolean) =>
        val ? <Tag color="success">Active</Tag> : <Tag color="default">Inactive</Tag>,
    },
    {
      title: 'Steps',
      key: 'steps',
      width: 100,
      align: 'center',
      render: (_, rec) => rec.steps?.length || 0,
    },
  ]

  return (
    <Card title="Workflow Definitions">
      <Typography.Paragraph type="secondary">
        This is a read-only view of workflow definitions configured in the system.
      </Typography.Paragraph>
      <Table
        dataSource={definitions}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 20 }}
      />
    </Card>
  )
}
