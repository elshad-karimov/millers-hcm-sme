// M494 — Permission matrix view. Read-only visualization of role × capability
// access levels (NONE / READ / WRITE / ADMIN). Available to SYSTEM_ADMIN,
// AUDITOR, and HR_ADMIN for understanding security boundaries.

import { useEffect, useState } from 'react'
import { App as AntdApp, Card, Empty, Space, Spin, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../api/client'

const { Title, Text } = Typography

type AccessLevel = 'NONE' | 'READ' | 'WRITE' | 'ADMIN'

interface PermissionRow {
  role: string
  capability: string
  level: AccessLevel
}

interface PermissionMatrix {
  matrix: Record<string, Record<string, AccessLevel>>
  toRows?: () => PermissionRow[]
}

const LEVEL_COLOR: Record<AccessLevel, string> = {
  NONE: 'default',
  READ: 'blue',
  WRITE: 'green',
  ADMIN: 'gold',
}

const LEVEL_SORT_ORDER: Record<AccessLevel, number> = {
  NONE: 0,
  READ: 1,
  WRITE: 2,
  ADMIN: 3,
}

export function PermissionMatrixPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(true)
  const [rows, setRows] = useState<PermissionRow[]>([])

  useEffect(() => {
    load()
  }, [])

  const load = () => {
    setLoading(true)
    api
      .get<PermissionMatrix>('/security/permission-matrix')
      .then((r) => {
        // Backend returns a PermissionMatrix record with a toRows() method in Java.
        // Client-side we need to flatten it ourselves:
        const matrix = r.data.matrix
        const flattened: PermissionRow[] = []
        Object.entries(matrix).forEach(([role, capabilities]) => {
          Object.entries(capabilities).forEach(([capability, level]) => {
            flattened.push({ role, capability, level })
          })
        })
        setRows(flattened)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load permission matrix'))
      .finally(() => setLoading(false))
  }

  const cols: ColumnsType<PermissionRow> = [
    {
      title: 'Role',
      dataIndex: 'role',
      width: 250,
      filters: Array.from(new Set(rows.map((r) => r.role))).map((role) => ({
        text: role,
        value: role,
      })),
      onFilter: (value, record) => record.role === value,
      render: (r: string) => <Text strong>{r}</Text>,
    },
    {
      title: 'Capability area',
      dataIndex: 'capability',
      filters: Array.from(new Set(rows.map((r) => r.capability))).map((cap) => ({
        text: cap,
        value: cap,
      })),
      onFilter: (value, record) => record.capability === value,
    },
    {
      title: 'Access level',
      dataIndex: 'level',
      width: 140,
      filters: ['NONE', 'READ', 'WRITE', 'ADMIN'].map((lv) => ({ text: lv, value: lv })),
      onFilter: (value, record) => record.level === value,
      sorter: (a, b) => LEVEL_SORT_ORDER[a.level] - LEVEL_SORT_ORDER[b.level],
      render: (l: AccessLevel) => <Tag color={LEVEL_COLOR[l]}>{l}</Tag>,
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Permission matrix</Title>

      <Card>
        <Space direction="vertical" size="small" style={{ width: '100%', marginBottom: 16 }}>
          <Text type="secondary">
            This matrix shows which access level each role has to each capability area. Use filters
            to explore a specific role or capability.
          </Text>
          <Space size={8}>
            <Tag color={LEVEL_COLOR.NONE}>NONE</Tag>
            <Tag color={LEVEL_COLOR.READ}>READ</Tag>
            <Tag color={LEVEL_COLOR.WRITE}>WRITE</Tag>
            <Tag color={LEVEL_COLOR.ADMIN}>ADMIN</Tag>
          </Space>
        </Space>
        <Table
          rowKey={(r) => `${r.role}-${r.capability}`}
          columns={cols}
          dataSource={rows}
          size="small"
          pagination={{ pageSize: 50 }}
          locale={{ emptyText: <Empty description="No matrix data" /> }}
        />
      </Card>
    </Space>
  )
}
