// HCM_12 M397 — calibration committee management (§19.1) + outlier /
// manager-leniency view. Committee CHAIR/MEMBERs (or HR admins) may calibrate;
// OBSERVERs and HR facilitators watch.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Drawer,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  calibrationApi,
  type CommitteeMember,
  type CommitteeRole,
  type ManagerStats,
  type OutlierEntry,
  type OutlierReport,
} from '../../api/calibrationApi'
import { employeesApi, type Employee } from '../../api/employees'

const { Text } = Typography

const ROLE_COLOR: Record<CommitteeRole, string> = {
  CHAIR: 'magenta',
  MEMBER: 'blue',
  OBSERVER: 'default',
  HR_FACILITATOR: 'purple',
}

export function CommitteeDrawer({
  sessionId,
  sessionName,
  open,
  canEdit,
  onClose,
}: {
  sessionId: string | null
  sessionName: string
  open: boolean
  canEdit: boolean
  onClose: () => void
}) {
  const { message } = AntdApp.useApp()
  const [members, setMembers] = useState<CommitteeMember[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(false)
  const [newEmployee, setNewEmployee] = useState<string | undefined>()
  const [newRole, setNewRole] = useState<CommitteeRole>('MEMBER')

  useEffect(() => {
    if (!open || !sessionId) return
    setLoading(true)
    Promise.all([calibrationApi.members(sessionId), employeesApi.list({ size: 500 })])
      .then(([m, e]) => {
        setMembers(m)
        setEmployees(Array.isArray(e) ? e : (e as { content: Employee[] }).content ?? [])
      })
      .catch(() => message.error('Failed to load committee'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, sessionId])

  const empName = (id: string) => {
    const e = employees.find((x) => x.id === id)
    return e ? `${e.employeeNo} — ${e.firstName} ${e.lastName}` : id
  }

  const add = async () => {
    if (!sessionId || !newEmployee) return
    try {
      await calibrationApi.addMember(sessionId, newEmployee, newRole)
      message.success('Committee member added')
      setNewEmployee(undefined)
      setMembers(await calibrationApi.members(sessionId))
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Add failed')
    }
  }

  const remove = async (memberId: string) => {
    if (!sessionId) return
    try {
      await calibrationApi.removeMember(sessionId, memberId)
      message.success('Removed')
      setMembers(await calibrationApi.members(sessionId))
    } catch {
      message.error('Remove failed')
    }
  }

  const columns: ColumnsType<CommitteeMember> = [
    { title: 'Member', key: 'employee', render: (_, m) => empName(m.employeeId) },
    {
      title: 'Role',
      dataIndex: 'memberRole',
      width: 140,
      render: (v: CommitteeRole) => <Tag color={ROLE_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>,
    },
    { title: 'Added by', dataIndex: 'addedBy', width: 120, render: (v) => v ?? '—' },
    ...(canEdit
      ? [
          {
            title: '',
            key: 'actions',
            width: 90,
            render: (_: unknown, m: CommitteeMember) => (
              <Button size="small" danger onClick={() => remove(m.id)}>
                Remove
              </Button>
            ),
          } as ColumnsType<CommitteeMember>[number],
        ]
      : []),
  ]

  return (
    <Drawer
      title={`Committee — ${sessionName} (§19.1)`}
      open={open}
      onClose={onClose}
      width={640}
    >
      <Text type="secondary">
        CHAIR / MEMBERs may calibrate in this session; OBSERVERs and HR facilitators have
        read-only access. HR admins can always calibrate.
      </Text>
      {canEdit && (
        <Space style={{ margin: '12px 0' }} wrap>
          <Select
            showSearch
            optionFilterProp="label"
            style={{ minWidth: 280 }}
            placeholder="Add employee…"
            value={newEmployee}
            onChange={setNewEmployee}
            options={employees
              .filter((e) => !members.some((m) => m.employeeId === e.id))
              .map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
          />
          <Select
            style={{ width: 160 }}
            value={newRole}
            onChange={setNewRole}
            options={(['CHAIR', 'MEMBER', 'OBSERVER', 'HR_FACILITATOR'] as CommitteeRole[]).map(
              (r) => ({ value: r, label: r.replace(/_/g, ' ') }),
            )}
          />
          <Button type="primary" disabled={!newEmployee} onClick={add}>
            Add
          </Button>
        </Space>
      )}
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={members}
        pagination={false}
        locale={{ emptyText: 'No committee members yet.' }}
      />
    </Drawer>
  )
}

export function OutliersCard({ cycleId }: { cycleId: string }) {
  const { message } = AntdApp.useApp()
  const [report, setReport] = useState<OutlierReport | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    calibrationApi
      .outliers(cycleId)
      .then(setReport)
      .catch(() => message.error('Failed to load outlier report'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cycleId])

  const mgrColumns: ColumnsType<ManagerStats> = [
    { title: 'Manager', key: 'name', render: (_, m) => m.managerName ?? m.managerId },
    { title: 'Rated', dataIndex: 'rated', width: 70, align: 'center' },
    {
      title: 'Avg rating',
      dataIndex: 'avgRating',
      width: 100,
      align: 'right',
      render: (v) => Number(v).toFixed(2),
    },
    {
      title: 'Δ vs cycle',
      dataIndex: 'deltaVsCycle',
      width: 110,
      align: 'right',
      render: (v: number) => {
        const n = Number(v)
        const color = Math.abs(n) >= 0.5 ? (n > 0 ? 'orange' : 'red') : 'green'
        return <Tag color={color}>{n > 0 ? '+' : ''}{n.toFixed(2)}</Tag>
      },
    },
  ]

  const outlierColumns: ColumnsType<OutlierEntry> = [
    { title: 'Employee', key: 'name', render: (_, o) => o.employeeName ?? o.employeeId },
    {
      title: 'Rating',
      dataIndex: 'rating',
      width: 90,
      align: 'right',
      render: (v) => Number(v).toFixed(2),
    },
    {
      title: 'Δ vs cycle avg',
      dataIndex: 'deltaVsCycle',
      width: 130,
      align: 'right',
      render: (v: number) => {
        const n = Number(v)
        return <Tag color={n > 0 ? 'cyan' : 'volcano'}>{n > 0 ? '+' : ''}{n.toFixed(2)}</Tag>
      },
    },
  ]

  return (
    <Card
      size="small"
      title={
        <Space size={8}>
          Outliers &amp; manager leniency
          {report?.cycleAverage != null && (
            <Tag>
              cycle avg {Number(report.cycleAverage).toFixed(2)} over {report.ratedCount} rated
            </Tag>
          )}
        </Space>
      }
      style={{ marginTop: 16 }}
      loading={loading}
    >
      <Space align="start" size={24} style={{ width: '100%' }} wrap>
        <div style={{ minWidth: 380, flex: 1 }}>
          <Text strong>Per-manager average (leniency / severity)</Text>
          <Table
            rowKey="managerId"
            size="small"
            columns={mgrColumns}
            dataSource={report?.managerStats ?? []}
            pagination={false}
            locale={{ emptyText: 'No rated reviews yet.' }}
          />
        </div>
        <div style={{ minWidth: 380, flex: 1 }}>
          <Text strong>Individual outliers (≥ 1.0 from cycle average)</Text>
          <Table
            rowKey="reviewId"
            size="small"
            columns={outlierColumns}
            dataSource={report?.outliers ?? []}
            pagination={false}
            locale={{ emptyText: 'No outliers.' }}
          />
        </div>
      </Space>
    </Card>
  )
}
