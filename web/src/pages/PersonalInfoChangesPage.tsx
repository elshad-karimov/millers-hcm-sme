// M79 — HR queue for personal-info change requests.

import { useEffect, useState } from 'react'
import {
  Card,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link } from 'react-router-dom'
import {
  personalInfoApi,
  type PersonalInfoChange,
  type PersonalInfoChangeStatus,
} from '../api/personalInfo'

const STATUS_COLOR: Record<PersonalInfoChangeStatus, string> = {
  PENDING: 'orange',
  APPROVED: 'blue',
  APPLIED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}

export function PersonalInfoChangesPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<PersonalInfoChange[]>([])
  const [loading, setLoading] = useState(true)
  const [status, setStatus] = useState<PersonalInfoChangeStatus | undefined>()
  const [page, setPage] = useState(0)
  const [size] = useState(20)
  const [total, setTotal] = useState(0)

  const load = () => {
    setLoading(true)
    personalInfoApi
      .list(status, page, size)
      .then((p) => {
        setRows(p.content)
        setTotal(p.totalElements)
      })
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load change requests'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(load, [status, page])

  const columns: ColumnsType<PersonalInfoChange> = [
    { title: 'Request no', dataIndex: 'requestNo', width: 140 },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (v: string) => <Link to={`/employees/${v}`}>{v.slice(0, 8)}…</Link>,
    },
    {
      title: 'Field',
      dataIndex: 'fieldKey',
      width: 180,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: 'From',
      dataIndex: 'oldValue',
      render: (v?: string | null) => (
        <Typography.Text type="secondary">{v ?? '—'}</Typography.Text>
      ),
    },
    {
      title: 'To',
      dataIndex: 'newValue',
      render: (v?: string | null) => v ?? '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (v: PersonalInfoChangeStatus) => (
        <Tag color={STATUS_COLOR[v]}>{v}</Tag>
      ),
    },
    {
      title: 'Submitted',
      dataIndex: 'submittedAt',
      width: 180,
      render: (v: string) => new Date(v).toLocaleString(),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={
          <Typography.Title level={4} style={{ margin: 0 }}>
            Personal-info change requests
          </Typography.Title>
        }
        extra={
          <Select
            placeholder="All statuses"
            allowClear
            style={{ width: 160 }}
            value={status}
            onChange={(v) => {
              setStatus(v)
              setPage(0)
            }}
            options={(
              ['PENDING', 'APPROVED', 'APPLIED', 'REJECTED', 'CANCELLED'] as PersonalInfoChangeStatus[]
            ).map((s) => ({ value: s, label: s }))}
          />
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize: size,
            total,
            onChange: (p) => setPage(p - 1),
            showSizeChanger: false,
          }}
        />
      </Card>
    </Space>
  )
}
