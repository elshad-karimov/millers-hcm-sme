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
import { leaveApi, type LeaveType } from '../api/leave'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

export function LeaveTypesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<LeaveType[]>([])
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    leaveApi
      .types()
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load leave types'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const columns: ColumnsType<LeaveType> = [
    { title: 'Code', dataIndex: 'code', width: 140 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Paid',
      dataIndex: 'paid',
      width: 70,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Paid' : 'Unpaid'}</Tag>,
    },
    {
      title: 'Annual entitlement',
      dataIndex: 'defaultAnnualEntitlementDays',
      width: 160,
      render: (v?: number | null) =>
        v === null || v === undefined ? '—' : `${v} day${v === 1 ? '' : 's'}`,
    },
    {
      title: 'Carry-forward limit',
      dataIndex: 'carryForwardLimitDays',
      width: 160,
      render: (v?: number | null) =>
        v === null || v === undefined ? 'Unlimited' : `${v} day${v === 1 ? '' : 's'}`,
    },
    {
      title: 'Accrual',
      width: 180,
      render: (_, r) => {
        if (!r.accruesMonthly) return <Tag color="default">one-shot</Tag>
        if (r.seniorityBrackets && r.seniorityBrackets.length > 0) {
          return (
            <Tag color="purple">
              {r.seniorityBrackets.length} seniority tier{r.seniorityBrackets.length > 1 ? 's' : ''}
            </Tag>
          )
        }
        const flat = r.monthlyAccrualDays ?? (r.defaultAnnualEntitlementDays != null ? +(r.defaultAnnualEntitlementDays / 12).toFixed(2) : null)
        return flat != null ? <Tag color="blue">{flat} d/mo</Tag> : <Tag color="blue">monthly</Tag>
      },
    },
    {
      title: 'Flags',
      render: (_, r) => (
        <Space size={[4, 4]} wrap>
          {r.requiresAttachment && <Tag>attachment</Tag>}
          {r.requiresReplacement && <Tag>replacement</Tag>}
          {r.excludeWeekends && <Tag>skip weekends</Tag>}
          {r.excludeHolidays && <Tag>skip holidays</Tag>}
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
            <Button size="small" onClick={() => navigate(`/leave/types/${r.id}/edit`)}>
              Edit
            </Button>
          ),
        }
      : { title: '', width: 0, render: () => null },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Leave types</Typography.Title>}
      extra={
        canEdit && (
          <Button type="primary" onClick={() => navigate('/leave/types/new')}>
            New leave type
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
