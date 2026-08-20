import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Modal,
  Space,
  Switch,
  Table,
  Tag,
  App as AntdApp,
  Popconfirm,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, TeamOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons'
import { api } from '../api/client'

interface ApprovalGroup {
  id: string
  code: string
  name: string
  active: boolean
  createdAt: string
  createdBy: string
}

interface ApprovalGroupMember {
  id: string
  groupId: string
  username: string
  createdAt: string
  createdBy: string
}

export function ApprovalGroupsPage() {
  const { message } = AntdApp.useApp()
  const [groups, setGroups] = useState<ApprovalGroup[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState<ApprovalGroup | null>(null)
  const [membersDrawer, setMembersDrawer] = useState<ApprovalGroup | null>(null)

  const [createForm] = Form.useForm()
  const [editForm] = Form.useForm()

  const fetchGroups = async () => {
    setLoading(true)
    try {
      const { data } = await api.get('/workflow/approval-groups')
      setGroups(data)
    } catch (err: any) {
      message.error(err.message || 'Failed to load approval groups')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchGroups()
  }, [])

  const handleCreate = async (values: any) => {
    try {
      await api.post('/workflow/approval-groups', {
        code: values.code,
        name: values.name,
      })
      message.success('Approval group created')
      setCreateOpen(false)
      createForm.resetFields()
      fetchGroups()
    } catch (err: any) {
      message.error(err.message || 'Failed to create approval group')
    }
  }

  const handleUpdate = async (groupId: string, values: any) => {
    try {
      await api.put(`/workflow/approval-groups/${groupId}`, {
        name: values.name,
        active: values.active,
      })
      message.success('Group updated')
      setEditOpen(null)
      editForm.resetFields()
      fetchGroups()
    } catch (err: any) {
      message.error(err.message || 'Failed to update group')
    }
  }

  const handleDelete = async (groupId: string) => {
    try {
      await api.delete(`/workflow/approval-groups/${groupId}`)
      message.success('Group deactivated')
      fetchGroups()
    } catch (err: any) {
      message.error(err.message || 'Failed to delete group')
    }
  }

  const columns: ColumnsType<ApprovalGroup> = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      width: 200,
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 100,
      render: (val: boolean) =>
        val ? <Tag color="success">Active</Tag> : <Tag color="default">Inactive</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 250,
      fixed: 'right',
      render: (_, rec) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<TeamOutlined />}
            onClick={() => setMembersDrawer(rec)}
          >
            Members
          </Button>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setEditOpen(rec)
              editForm.setFieldsValue({ name: rec.name, active: rec.active })
            }}
          >
            Edit
          </Button>
          <Popconfirm
            title="Deactivate this group?"
            onConfirm={() => handleDelete(rec.id)}
            okText="Yes"
            cancelText="No"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Card
        title="Approval Groups"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            New Group
          </Button>
        }
      >
        <Table
          dataSource={groups}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      {/* Create Modal */}
      <Modal
        title="New Approval Group"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          createForm.resetFields()
        }}
        onOk={() => createForm.submit()}
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreate}>
          <Form.Item
            name="code"
            label="Code"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="e.g. FINANCE_APPROVERS" />
          </Form.Item>
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="e.g. Finance Team Approvers" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        title={`Edit Group: ${editOpen?.code}`}
        open={!!editOpen}
        onCancel={() => {
          setEditOpen(null)
          editForm.resetFields()
        }}
        onOk={() => editForm.submit()}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={(values) => {
            if (editOpen) handleUpdate(editOpen.id, values)
          }}
        >
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      {/* Members Drawer */}
      {membersDrawer && (
        <MembersDrawer
          group={membersDrawer}
          open={!!membersDrawer}
          onClose={() => setMembersDrawer(null)}
        />
      )}
    </>
  )
}

interface MembersDrawerProps {
  group: ApprovalGroup
  open: boolean
  onClose: () => void
}

function MembersDrawer({ group, open, onClose }: MembersDrawerProps) {
  const { message } = AntdApp.useApp()
  const [members, setMembers] = useState<ApprovalGroupMember[]>([])
  const [loading, setLoading] = useState(false)
  const [addOpen, setAddOpen] = useState(false)

  const [addForm] = Form.useForm()

  const fetchMembers = async () => {
    setLoading(true)
    try {
      const { data } = await api.get(`/workflow/approval-groups/${group.id}/members`)
      setMembers(data)
    } catch (err: any) {
      message.error(err.message || 'Failed to load members')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) {
      fetchMembers()
    }
  }, [open])

  const handleAdd = async (values: any) => {
    try {
      await api.post(`/workflow/approval-groups/${group.id}/members`, {
        username: values.username,
      })
      message.success('Member added')
      setAddOpen(false)
      addForm.resetFields()
      fetchMembers()
    } catch (err: any) {
      message.error(err.message || 'Failed to add member')
    }
  }

  const handleRemove = async (memberId: string) => {
    try {
      // The remove-member route hangs off the approval-groups controller —
      // DELETE /workflow/approval-groups/members/{id}. Called without that
      // segment it 404s, so members could be added but never taken off.
      await api.delete(`/workflow/approval-groups/members/${memberId}`)
      message.success('Member removed')
      fetchMembers()
    } catch (err: any) {
      message.error(err.message || 'Failed to remove member')
    }
  }

  const columns: ColumnsType<ApprovalGroupMember> = [
    {
      title: 'Username',
      dataIndex: 'username',
      key: 'username',
    },
    {
      title: 'Action',
      key: 'action',
      width: 100,
      fixed: 'right',
      render: (_, rec) => (
        <Popconfirm
          title="Remove this member?"
          onConfirm={() => handleRemove(rec.id)}
          okText="Yes"
          cancelText="No"
        >
          <Button type="link" size="small" danger icon={<DeleteOutlined />}>
            Remove
          </Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <>
      <Drawer
        title={`Members: ${group.name}`}
        open={open}
        onClose={onClose}
        width={600}
        extra={
          <Button type="primary" size="small" onClick={() => setAddOpen(true)}>
            Add Member
          </Button>
        }
      >
        <Table
          dataSource={members}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
        />
      </Drawer>

      <Modal
        title="Add Member"
        open={addOpen}
        onCancel={() => {
          setAddOpen(false)
          addForm.resetFields()
        }}
        onOk={() => addForm.submit()}
      >
        <Form form={addForm} layout="vertical" onFinish={handleAdd}>
          <Form.Item
            name="username"
            label="Username"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="Enter username" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
