import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Progress,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import {
  positionsApi,
  type Position,
  type PositionStatus,
  type VacancyState,
} from '../api/positions'
import { useAuth } from '../auth/AuthContext'

const VACANCY_OPTIONS: VacancyState[] = [
  'OCCUPIED',
  'VACANT',
  'PARTIALLY_OCCUPIED',
  'FROZEN',
  'PLANNED',
  'CANCELLED',
]

const VACANCY_COLOR: Record<VacancyState, string> = {
  OCCUPIED: 'green',
  VACANT: 'gold',
  PARTIALLY_OCCUPIED: 'blue',
  FROZEN: 'orange',
  PLANNED: 'purple',
  CANCELLED: 'default',
}

const STATUS_COLOR: Record<PositionStatus, string> = {
  ACTIVE: 'green',
  CLOSED: 'default',
}

export function PositionsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole('HR_ADMIN', 'HR_SPECIALIST')

  const [rows, setRows] = useState<Position[]>([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [total, setTotal] = useState(0)
  const [search, setSearch] = useState('')
  const [vacancyState, setVacancyState] = useState<VacancyState | undefined>()
  const [status, setStatus] = useState<PositionStatus | undefined>()

  const load = () => {
    setLoading(true)
    positionsApi
      .list({
        page,
        size,
        search: search || undefined,
        vacancyState,
        status,
      })
      .then((res) => {
        setRows(res.content)
        setTotal(res.totalElements)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load positions'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, vacancyState, status])

  const columns: ColumnsType<Position> = [
    { title: 'Code', dataIndex: 'code', width: 110 },
    { title: 'Title', dataIndex: 'title' },
    {
      title: 'Org unit',
      dataIndex: 'orgUnitLabel',
      render: (v?: string | null) => v || '—',
    },
    {
      title: 'Headcount',
      render: (_, r) => (
        <Space direction="vertical" size={0} style={{ width: 120 }}>
          <Typography.Text>
            {r.occupiedHeadcount} / {r.approvedHeadcount}
          </Typography.Text>
          <Progress
            percent={
              r.approvedHeadcount === 0
                ? 0
                : Math.round((r.occupiedHeadcount / r.approvedHeadcount) * 100)
            }
            size="small"
            showInfo={false}
          />
        </Space>
      ),
    },
    {
      title: 'Salary',
      render: (_, r) =>
        r.salaryMin || r.salaryMax
          ? `${r.salaryMin ?? '—'} – ${r.salaryMax ?? '—'} ${r.currency}`
          : '—',
    },
    {
      title: 'Vacancy',
      dataIndex: 'vacancyState',
      render: (s: VacancyState) => (
        <Tag color={VACANCY_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: PositionStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    canEdit
      ? {
          title: '',
          width: 80,
          render: (_, r) => (
            <Button
              size="small"
              onClick={() => navigate(`/positions/${r.id}/edit`)}
              disabled={r.status === 'CLOSED'}
            >
              Edit
            </Button>
          ),
        }
      : { title: '', render: () => null, width: 0 },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Positions</Typography.Title>}
      extra={
        canEdit && (
          <Button type="primary" onClick={() => navigate('/positions/new')}>
            New position
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Search by title or code"
          allowClear
          onSearch={(v) => {
            setSearch(v)
            setPage(0)
            load()
          }}
          style={{ width: 280 }}
        />
        <Select
          allowClear
          placeholder="Vacancy state"
          style={{ width: 200 }}
          options={VACANCY_OPTIONS.map((s) => ({ value: s, label: s.replace(/_/g, ' ') }))}
          value={vacancyState}
          onChange={(v) => {
            setVacancyState(v)
            setPage(0)
          }}
        />
        <Select
          allowClear
          placeholder="Status"
          style={{ width: 160 }}
          options={[
            { value: 'ACTIVE', label: 'Active' },
            { value: 'CLOSED', label: 'Closed' },
          ]}
          value={status}
          onChange={(v) => {
            setStatus(v as PositionStatus | undefined)
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
