import { useEffect, useState } from 'react'
import { Card, Table, Tag, App as AntdApp } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { ppeItemsApi, type PpeItemResponse, type PpeType, PPE_TYPE_OPTIONS } from '../api/ehs'

export function PpeItemsPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [items, setItems] = useState<PpeItemResponse[]>([])

  useEffect(() => {
    loadItems()
  }, [])

  const loadItems = async () => {
    setLoading(true)
    try {
      const data = await ppeItemsApi.list()
      setItems(data)
    } catch (err) {
      message.error('Failed to load PPE items')
    } finally {
      setLoading(false)
    }
  }

  const columns: ColumnsType<PpeItemResponse> = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      width: 120,
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      width: 300,
    },
    {
      title: 'Type',
      dataIndex: 'ppeType',
      key: 'ppeType',
      width: 180,
      render: (val: PpeType) =>
        PPE_TYPE_OPTIONS.find((o) => o.value === val)?.label || val,
    },
    {
      title: 'Default expiry (months)',
      dataIndex: 'defaultExpiryMonths',
      key: 'defaultExpiryMonths',
      width: 180,
      render: (val) => val || '—',
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 100,
      render: (val: boolean) => (
        <Tag color={val ? 'green' : 'default'}>{val ? 'Yes' : 'No'}</Tag>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card title="PPE Items (Catalog)">
        <Table
          loading={loading}
          dataSource={items}
          columns={columns}
          rowKey="id"
          pagination={{ pageSize: 20 }}
        />
      </Card>
    </div>
  )
}
