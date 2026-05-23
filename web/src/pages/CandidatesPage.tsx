import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Input,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { recruitmentApi, type Candidate, type CandidateSource } from '../api/recruitment'
import { useAuth } from '../auth/AuthContext'

export function CandidatesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole('HR_ADMIN', 'HR_SPECIALIST', 'RECRUITER')

  const [rows, setRows] = useState<Candidate[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    recruitmentApi
      .candidates({ search: search || undefined, page, size })
      .then((res) => {
        setRows(res.content)
        setTotal(res.totalElements)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load candidates'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size])

  const columns: ColumnsType<Candidate> = [
    { title: 'Candidate #', dataIndex: 'candidateNo', width: 130 },
    {
      title: 'Name',
      render: (_, r) => `${r.lastName}, ${r.firstName}${r.middleName ? ' ' + r.middleName : ''}`,
    },
    { title: 'Email', dataIndex: 'email' },
    { title: 'Phone', dataIndex: 'phone' },
    {
      title: 'Source',
      dataIndex: 'source',
      width: 110,
      render: (s?: CandidateSource | null) => (s ? <Tag>{s.replace(/_/g, ' ')}</Tag> : '—'),
    },
    { title: 'Experience', dataIndex: 'experienceYears', render: (v?: number | null) => (v ? `${v}y` : '—'), width: 100 },
    {
      title: 'Expected',
      render: (_, r) => (r.expectedSalary ? `${r.expectedSalary} ${r.currency}` : '—'),
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Candidates</Typography.Title>}
      extra={
        canEdit && (
          <Button type="primary" onClick={() => navigate('/recruitment/candidates/new')}>
            New candidate
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="Search by name or email"
          allowClear
          style={{ width: 320 }}
          onSearch={(v) => {
            setSearch(v)
            setPage(0)
            load()
          }}
        />
      </Space>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: size,
          total,
          onChange: (p, s) => {
            setPage(p - 1)
            setSize(s)
          },
          showSizeChanger: true,
        }}
      />
    </Card>
  )
}
