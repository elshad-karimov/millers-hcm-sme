// M77 — HR letter request queue (HR / scoped manager view).

import { useEffect, useState } from 'react'
import {
  Button,
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
  letterRequestsApi,
  type LetterRequest,
  type LetterStatus,
} from '../api/letters'

const STATUS_COLOR: Record<LetterStatus, string> = {
  DRAFT: 'default',
  PENDING: 'orange',
  APPROVED: 'blue',
  ISSUED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}

export function LetterRequestsPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<LetterRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [status, setStatus] = useState<LetterStatus | undefined>()
  const [page, setPage] = useState(0)
  const [size] = useState(20)
  const [total, setTotal] = useState(0)

  const load = () => {
    setLoading(true)
    letterRequestsApi
      .list(status, page, size)
      .then((p) => {
        setRows(p.content)
        setTotal(p.totalElements)
      })
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load letter requests'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(load, [status, page])

  const columns: ColumnsType<LetterRequest> = [
    {
      title: 'Request no',
      dataIndex: 'requestNo',
      width: 140,
    },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (v: string) => <Link to={`/employees/${v}`}>{v.slice(0, 8)}…</Link>,
    },
    { title: 'Purpose', dataIndex: 'purpose', render: (v) => v ?? '—' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (v: LetterStatus) => <Tag color={STATUS_COLOR[v]}>{v}</Tag>,
    },
    {
      title: 'Requested',
      dataIndex: 'requestedAt',
      width: 180,
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: '',
      width: 260,
      render: (_, r) =>
        r.status === 'ISSUED' ? (
          <Space size={4}>
            <Button
              size="small"
              type="link"
              href={letterRequestsApi.bodyUrl(r.id)}
              target="_blank"
              rel="noreferrer"
            >
              Download text
            </Button>
            {/* M139 — PDF with signature + QR */}
            <Button
              size="small"
              type="link"
              href={letterRequestsApi.pdfUrl(r.id)}
              target="_blank"
              rel="noreferrer"
            >
              PDF
            </Button>
          </Space>
        ) : null,
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={
          <Typography.Title level={4} style={{ margin: 0 }}>
            Letter requests
          </Typography.Title>
        }
        extra={
          <Space>
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
                ['PENDING', 'APPROVED', 'ISSUED', 'REJECTED', 'CANCELLED'] as LetterStatus[]
              ).map((s) => ({ value: s, label: s }))}
            />
          </Space>
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
