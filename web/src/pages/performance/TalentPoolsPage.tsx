// HCM_15 M410 — employee talent pools (PRD §15.5/§15.6). HR-only grouping:
// HiPo cohorts, succession bench, mobility registry, leadership pipeline.
// Distinct from recruitment candidate pools (M87).

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../../api/client'
import { employeesApi, type Employee } from '../../api/employees'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title } = Typography
const { TextArea } = Input

interface EmployeeTalentPool {
  id: string
  code: string
  name: string
  purpose?: string
  ownerEmployeeId?: string
  active: boolean
  createdBy?: string
  createdAt: string
}

interface PoolMember {
  id: string
  poolId: string
  employeeId: string
  addedBy?: string
  addedAt: string
  note?: string
}

export function TalentPoolsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [pools, setPools] = useState<EmployeeTalentPool[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(true)

  const [poolOpen, setPoolOpen] = useState(false)
  const [editingPool, setEditingPool] = useState<EmployeeTalentPool | null>(null)
  const [poolForm] = Form.useForm()

  const [viewingPool, setViewingPool] = useState<EmployeeTalentPool | null>(null)
  const [members, setMembers] = useState<PoolMember[]>([])
  const [loadingMembers, setLoadingMembers] = useState(false)

  const [addMemberOpen, setAddMemberOpen] = useState(false)
  const [memberForm] = Form.useForm()

  const load = () => {
    setLoading(true)
    Promise.all([
      api.get<EmployeeTalentPool[]>('/talent/pools'),
      employeesApi.list({ size: 1000 }),
    ])
      .then(([p, e]) => {
        setPools(p.data)
        setEmployees(Array.isArray(e) ? e : (e as { content: Employee[] }).content ?? [])
      })
      .catch(() => message.error('Failed to load talent pools'))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line react-hooks/exhaustive-deps

  const loadMembers = (poolId: string) => {
    setLoadingMembers(true)
    api
      .get<PoolMember[]>(`/talent/pools/${poolId}/members`)
      .then((res) => setMembers(res.data))
      .catch(() => message.error('Failed to load pool members'))
      .finally(() => setLoadingMembers(false))
  }

  const empName = (id?: string) => {
    if (!id) return '—'
    const e = employees.find((e) => e.id === id)
    return e ? `${e.firstName} ${e.lastName}` : id
  }

  const handleSavePool = () => {
    poolForm.validateFields().then((values) => {
      const req = editingPool
        ? api.put(`/talent/pools/${editingPool.id}`, values)
        : api.post('/talent/pools', values)
      req
        .then(() => {
          message.success(editingPool ? 'Pool updated' : 'Pool created')
          setPoolOpen(false)
          setEditingPool(null)
          poolForm.resetFields()
          load()
        })
        .catch(() => message.error('Failed to save pool'))
    })
  }

  const handleAddMember = () => {
    if (!viewingPool) return
    memberForm.validateFields().then((values) => {
      api
        .post(`/talent/pools/${viewingPool.id}/members`, values)
        .then(() => {
          message.success('Member added')
          setAddMemberOpen(false)
          memberForm.resetFields()
          loadMembers(viewingPool.id)
        })
        .catch(() => message.error('Failed to add member'))
    })
  }

  const handleRemoveMember = (poolId: string, employeeId: string) => {
    Modal.confirm({
      title: 'Remove from pool?',
      content: 'This will remove the employee from this talent pool.',
      okText: 'Remove',
      okType: 'danger',
      onOk: () => {
        api
          .delete(`/talent/pools/${poolId}/members/${employeeId}`)
          .then(() => {
            message.success('Member removed')
            loadMembers(poolId)
          })
          .catch(() => message.error('Failed to remove member'))
      },
    })
  }

  const poolColumns: ColumnsType<EmployeeTalentPool> = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 120,
      sorter: (a, b) => a.code.localeCompare(b.code),
    },
    {
      title: 'Name',
      dataIndex: 'name',
      sorter: (a, b) => a.name.localeCompare(b.name),
    },
    {
      title: 'Purpose',
      dataIndex: 'purpose',
      ellipsis: true,
    },
    {
      title: 'Owner',
      dataIndex: 'ownerEmployeeId',
      render: (id) => empName(id),
    },
    {
      title: 'Status',
      dataIndex: 'active',
      width: 100,
      render: (active) => (
        <Tag color={active ? 'green' : 'default'}>{active ? 'Active' : 'Inactive'}</Tag>
      ),
    },
    {
      title: 'Actions',
      width: 160,
      render: (_, pool) => (
        <Space>
          <Button
            size="small"
            onClick={() => {
              setViewingPool(pool)
              loadMembers(pool.id)
            }}
          >
            Members
          </Button>
          {canWrite && (
            <Button
              size="small"
              onClick={() => {
                setEditingPool(pool)
                poolForm.setFieldsValue(pool)
                setPoolOpen(true)
              }}
            >
              Edit
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const memberColumns: ColumnsType<PoolMember> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id) => empName(id),
    },
    {
      title: 'Note',
      dataIndex: 'note',
      ellipsis: true,
    },
    {
      title: 'Added By',
      dataIndex: 'addedBy',
      width: 150,
    },
    {
      title: 'Added At',
      dataIndex: 'addedAt',
      width: 180,
      render: (v) => new Date(v).toLocaleString(),
    },
    {
      title: 'Actions',
      width: 100,
      render: (_, m) =>
        canWrite && (
          <Button
            size="small"
            danger
            onClick={() => handleRemoveMember(m.poolId, m.employeeId)}
          >
            Remove
          </Button>
        ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Title level={2}>Talent Pools</Title>
        {canWrite && (
          <Button
            type="primary"
            onClick={() => {
              setEditingPool(null)
              poolForm.resetFields()
              setPoolOpen(true)
            }}
          >
            New Pool
          </Button>
        )}
      </div>

      <Card>
        <Table
          dataSource={pools}
          columns={poolColumns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Modal
        title={editingPool ? 'Edit Pool' : 'New Pool'}
        open={poolOpen}
        onOk={handleSavePool}
        onCancel={() => {
          setPoolOpen(false)
          setEditingPool(null)
          poolForm.resetFields()
        }}
        width={600}
      >
        <Form form={poolForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="code"
            label="Code"
            rules={[{ required: true, message: 'Code is required' }]}
          >
            <Input placeholder="HIPO_2026" disabled={!!editingPool} />
          </Form.Item>
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Name is required' }]}
          >
            <Input placeholder="HiPo Cohort 2026" />
          </Form.Item>
          <Form.Item name="purpose" label="Purpose">
            <TextArea rows={3} placeholder="Description of this talent pool..." />
          </Form.Item>
          <Form.Item name="ownerEmployeeId" label="Owner (HR Analyst)">
            <Select
              showSearch
              allowClear
              placeholder="Select owner"
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.firstName} ${e.lastName} (${e.employeeNo})`,
              }))}
            />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={viewingPool?.name ?? 'Pool Members'}
        open={!!viewingPool}
        onClose={() => {
          setViewingPool(null)
          setMembers([])
        }}
        width={800}
        extra={
          canWrite && viewingPool && (
            <Button
              type="primary"
              onClick={() => {
                memberForm.resetFields()
                setAddMemberOpen(true)
              }}
            >
              Add Member
            </Button>
          )
        }
      >
        {viewingPool && (
          <>
            <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Code">{viewingPool.code}</Descriptions.Item>
              <Descriptions.Item label="Purpose">{viewingPool.purpose ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Owner">{empName(viewingPool.ownerEmployeeId)}</Descriptions.Item>
            </Descriptions>
            <Table
              dataSource={members}
              columns={memberColumns}
              rowKey="id"
              loading={loadingMembers}
              pagination={{ pageSize: 20 }}
            />
          </>
        )}
      </Drawer>

      <Modal
        title="Add Member"
        open={addMemberOpen}
        onOk={handleAddMember}
        onCancel={() => {
          setAddMemberOpen(false)
          memberForm.resetFields()
        }}
      >
        <Form form={memberForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Employee is required' }]}
          >
            <Select
              showSearch
              placeholder="Select employee"
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.firstName} ${e.lastName} (${e.employeeNo})`,
              }))}
            />
          </Form.Item>
          <Form.Item name="note" label="Note">
            <TextArea rows={2} placeholder="Optional note..." />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
