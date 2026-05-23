import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { permissionApi, type PermissionType } from '../api/permission'
import { useAuth } from '../auth/AuthContext'

export function PermissionTypesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole('HR_ADMIN', 'SYSTEM_ADMIN')

  const [rows, setRows] = useState<PermissionType[]>([])
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    permissionApi
      .types()
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load permission types'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const columns: ColumnsType<PermissionType> = [
    { title: 'Code', dataIndex: 'code', width: 140 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Annual limit',
      dataIndex: 'annualLimitHours',
      width: 130,
      render: (v?: number | null) => (v === null || v === undefined ? 'Unlimited' : `${v}h / year`),
    },
    {
      title: 'Paid',
      dataIndex: 'paid',
      width: 80,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Paid' : 'Unpaid'}</Tag>,
    },
    {
      title: 'Flags',
      render: (_, r) => (
        <Space size={[4, 4]} wrap>
          {r.requiresAttachment && <Tag>attachment</Tag>}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) => (
        <Tag color={v ? 'green' : 'default'}>{v ? 'Active' : 'Disabled'}</Tag>
      ),
    },
    canEdit
      ? {
          title: '',
          width: 80,
          render: (_, r) => (
            <Button size="small" onClick={() => navigate(`/permission/types/${r.id}/edit`)}>
              Edit
            </Button>
          ),
        }
      : { title: '', width: 0, render: () => null },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Permission types</Typography.Title>}
      extra={
        canEdit && (
          <Button type="primary" onClick={() => navigate('/permission/types/new')}>
            New permission type
          </Button>
        )
      }
    >
      <Table
        rowKey="id"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={false}
      />
    </Card>
  )
}
