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
import { useNavigate } from 'react-router-dom'
import { recruitmentApi, type Vacancy, type VacancyStatus } from '../api/recruitment'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const STATUS_OPTIONS: VacancyStatus[] = [
  'DRAFT',
  'PENDING_APPROVAL',
  'APPROVED',
  'REJECTED',
  'OPEN',
  'PUBLISHED',
  'PAUSED',
  'ON_HOLD',
  'CLOSED',
  'FILLED',
  'CANCELLED',
]

const STATUS_COLOR: Record<VacancyStatus, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'cyan',
  REJECTED: 'red',
  OPEN: 'green',
  PUBLISHED: 'green',
  PAUSED: 'orange',
  ON_HOLD: 'orange',
  CLOSED: 'default',
  FILLED: 'blue',
  CANCELLED: 'red',
}

export function VacanciesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole(...RoleSets.RECRUITMENT_TEAM)

  const [rows, setRows] = useState<Vacancy[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [status, setStatus] = useState<VacancyStatus | undefined>()
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    recruitmentApi
      .vacancies({ status, page, size })
      .then((res) => {
        setRows(res.content)
        setTotal(res.totalElements)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load vacancies'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, status])

  const columns: ColumnsType<Vacancy> = [
    { title: 'Vacancy #', dataIndex: 'vacancyNo', width: 110 },
    { title: 'Title', dataIndex: 'title' },
    { title: 'Department', dataIndex: 'department' },
    { title: 'Location', dataIndex: 'location' },
    { title: 'Openings', dataIndex: 'openings', align: 'right', width: 90 },
    {
      title: 'Salary',
      render: (_, r) =>
        r.salaryMin || r.salaryMax
          ? `${r.salaryMin ?? '—'} – ${r.salaryMax ?? '—'} ${r.currency}`
          : '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: VacancyStatus) => <Tag color={STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>,
      width: 110,
    },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <Button size="small" onClick={() => navigate(`/recruitment/vacancies/${r.id}`)}>
          Open
        </Button>
      ),
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Vacancies</Typography.Title>}
      extra={
        canEdit && (
          <Button type="primary" onClick={() => navigate('/recruitment/vacancies/new')}>
            New vacancy
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          allowClear
          placeholder="All statuses"
          style={{ width: 180 }}
          options={STATUS_OPTIONS.map((s) => ({ value: s, label: s.replace(/_/g, ' ') }))}
          value={status}
          onChange={(v) => {
            setStatus(v)
            setPage(0)
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
