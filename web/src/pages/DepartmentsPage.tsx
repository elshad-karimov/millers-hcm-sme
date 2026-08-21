import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Typography,
  App as AntdApp,
} from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { departmentsApi, type Department } from '../api/departments'
import { apiErrorDuration, apiErrorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { Roles } from '../auth/roleSets'

/**
 * Departments — master data.
 *
 * Deliberately a plain list. Org Structure already offers the full versioned
 * tree with its approval cycle, which is the right tool for a reorganisation
 * and the wrong one for adding a department: three structure versions had been
 * started here and none activated, so the list stayed empty and the employee
 * screen fell back to typed text.
 */
export function DepartmentsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(Roles.SYSTEM_ADMIN, Roles.HR_ADMIN)

  const [rows, setRows] = useState<Department[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Department | null>(null)
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    departmentsApi
      .list()
      .then(setRows)
      .catch((err) => message.error(apiErrorMessage(err, 'Could not load departments')))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const startAdd = () => {
    setEditing(null)
    setCode('')
    setName('')
    setOpen(true)
  }

  const startEdit = (row: Department) => {
    setEditing(row)
    setCode(row.code)
    setName(row.name)
    setOpen(true)
  }

  const save = async () => {
    if (!code.trim() || !name.trim()) {
      message.error('Code and name are both required')
      return
    }
    setSaving(true)
    try {
      if (editing) {
        await departmentsApi.rename(editing.id, { code: code.trim(), name: name.trim() })
        message.success('Department updated')
      } else {
        await departmentsApi.create({ code: code.trim(), name: name.trim() })
        message.success('Department added')
      }
      setOpen(false)
      load()
    } catch (err) {
      message.error(apiErrorMessage(err, 'Could not save the department'), apiErrorDuration(err))
    } finally {
      setSaving(false)
    }
  }

  const remove = async (row: Department) => {
    try {
      await departmentsApi.remove(row.id)
      message.success('Department removed')
      load()
    } catch (err) {
      message.error(apiErrorMessage(err, 'Could not remove the department'), apiErrorDuration(err))
    }
  }

  const columns: ColumnsType<Department> = [
    { title: 'Code', dataIndex: 'code', width: 180 },
    { title: 'Name', dataIndex: 'name' },
    ...(canEdit
      ? [
          {
            title: '',
            width: 120,
            render: (_: unknown, row: Department) => (
              <Space>
                <Button size="small" icon={<EditOutlined />} onClick={() => startEdit(row)} />
                <Popconfirm
                  title="Remove this department?"
                  description="Employees already recorded against it keep the department name on their record."
                  onConfirm={() => remove(row)}
                >
                  <Button size="small" danger icon={<DeleteOutlined />} />
                </Popconfirm>
              </Space>
            ),
          },
        ]
      : []),
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          Departments
        </Typography.Title>
        {canEdit && (
          <Button type="primary" icon={<PlusOutlined />} onClick={startAdd}>
            New department
          </Button>
        )}
      </Space>

      <Alert
        type="info"
        showIcon
        message="This is the list the employee screen picks from"
        description="Add a department here and it becomes selectable on an employee straight away. Org Structure shows the same units as a tree, with the full approval cycle, for a reorganisation."
      />

      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{
          emptyText: (
            <Empty description="No departments yet. Add the first one to fill the Department list on the employee screen." />
          ),
        }}
      />

      <Modal
        title={editing ? 'Edit department' : 'New department'}
        open={open}
        okText="Save"
        confirmLoading={saving}
        onCancel={() => setOpen(false)}
        onOk={save}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <div>
            <div style={{ marginBottom: 4 }}>Code</div>
            <Input
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="e.g. ENG"
              disabled={!!editing}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>Name</div>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Engineering"
            />
          </div>
        </Space>
      </Modal>
    </Space>
  )
}
