import { useEffect, useState } from 'react'
import {
  Badge,
  Button,
  Card,
  Checkbox,
  Col,
  message,
  Modal,
  Row,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { EditOutlined, KeyOutlined, TeamOutlined } from '@ant-design/icons'
import { adminApi, type AdminUser } from '../api/admin'
import { brand } from '../theme'

const { Title, Text } = Typography

/** Colour palette per role — mirrors the header role tags. */
const ROLE_COLORS: Record<string, string> = {
  SYSTEM_ADMIN: '#722ed1',
  HR_ADMIN: '#1677ff',
  HR_SPECIALIST: '#0958d9',
  AUDITOR: '#d48806',
  DEPARTMENT_MANAGER: '#08979c',
  EMPLOYEE: '#389e0d',
}

const ALL_ROLES = [
  'SYSTEM_ADMIN',
  'HR_ADMIN',
  'HR_SPECIALIST',
  'AUDITOR',
  'DEPARTMENT_MANAGER',
  'EMPLOYEE',
]

export function UserManagementPage() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  // Edit modal
  const [editUser, setEditUser] = useState<AdminUser | null>(null)
  // Password state, deliberately narrow: who it is for, the value we were
  // handed once, and whether the call is in flight. Nothing is cached — close
  // the dialog and the value is gone, because Keycloak will not return it again.
  const [pwUser, setPwUser] = useState<AdminUser | null>(null)
  const [pwValue, setPwValue] = useState<string | null>(null)
  const [pwLoading, setPwLoading] = useState<string | null>(null)
  const [selectedRoles, setSelectedRoles] = useState<string[]>([])

  useEffect(() => {
    void fetchUsers()
  }, [])

  async function fetchUsers() {
    setLoading(true)
    try {
      const data = await adminApi.listUsers()
      setUsers(data)
    } catch {
      void message.error('Failed to load users')
    } finally {
      setLoading(false)
    }
  }

  function openEdit(user: AdminUser) {
    setEditUser(user)
    setSelectedRoles([...user.roles])
  }

  async function handleSaveRoles() {
    if (!editUser) return
    setSaving(true)
    try {
      await adminApi.setUserRoles(editUser.id, selectedRoles)
      void message.success(`Roles updated for ${editUser.username}`)
      setEditUser(null)
      await fetchUsers()
    } catch {
      void message.error('Failed to update roles')
    } finally {
      setSaving(false)
    }
  }

  /**
   * Accounts created for an employee have no password, so they cannot sign in
   * until someone gives them a first one. The proper route is a password-setup
   * email from Keycloak; where the realm has no mail server this is the only
   * way in that does not require a Keycloak admin console account.
   */
  const issuePassword = (u: AdminUser) => {
    setPwLoading(u.id)
    adminApi
      .issueTemporaryPassword(u.id)
      .then((password) => {
        setPwUser(u)
        setPwValue(password)
      })
      .catch(() => void message.error(`Could not set a password for ${u.username}`))
      .finally(() => setPwLoading(null))
  }

  const columns: ColumnsType<AdminUser> = [
    {
      title: 'Username',
      dataIndex: 'username',
      key: 'username',
      render: (v: string) => <Text strong>{v}</Text>,
      sorter: (a, b) => a.username.localeCompare(b.username),
    },
    {
      title: 'Name',
      key: 'name',
      render: (_: unknown, r: AdminUser) =>
        [r.firstName, r.lastName].filter(Boolean).join(' ') || '—',
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
      render: (v: string) => v || '—',
    },
    {
      title: 'Status',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 100,
      render: (v: boolean) => (
        <Badge status={v ? 'success' : 'default'} text={v ? 'Active' : 'Disabled'} />
      ),
    },
    {
      title: 'Roles',
      dataIndex: 'roles',
      key: 'roles',
      render: (roles: string[]) =>
        roles.length === 0 ? (
          <Text type="secondary">—</Text>
        ) : (
          <Space wrap>
            {roles.map((r) => (
              <Tag
                key={r}
                style={{
                  background: `${ROLE_COLORS[r] ?? brand.purple}18`,
                  borderColor: `${ROLE_COLORS[r] ?? brand.purple}40`,
                  color: ROLE_COLORS[r] ?? brand.purple,
                  fontWeight: 500,
                }}
              >
                {r}
              </Tag>
            ))}
          </Space>
        ),
    },
    {
      title: '',
      key: 'actions',
      width: 200,
      render: (_: unknown, r: AdminUser) => (
        <Space size="small">
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEdit(r)}
          >
            Edit
          </Button>
          <Button
            size="small"
            icon={<KeyOutlined />}
            loading={pwLoading === r.id}
            onClick={() => issuePassword(r)}
          >
            Set password
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ maxWidth: 1100 }}>
      {/* Page header */}
      <Row align="middle" justify="space-between" style={{ marginBottom: 20 }}>
        <Col>
          <Space align="center">
            <TeamOutlined style={{ fontSize: 22, color: brand.purple }} />
            <Title level={4} style={{ margin: 0 }}>
              User Management
            </Title>
          </Space>
          <Text type="secondary" style={{ display: 'block', marginTop: 2 }}>
            Assign or revoke Keycloak realm roles for each user
          </Text>
        </Col>
      </Row>

      <Card>
        <Table<AdminUser>
          rowKey="id"
          columns={columns}
          dataSource={users}
          loading={loading}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          size="small"
        />
      </Card>

      {/* Role-edit modal */}
      <Modal
        title={
          editUser ? (
            <Space>
              <EditOutlined style={{ color: brand.purple }} />
              Edit roles — <Text strong>{editUser.username}</Text>
            </Space>
          ) : null
        }
        open={editUser !== null}
        onCancel={() => setEditUser(null)}
        footer={[
          <Button key="cancel" onClick={() => setEditUser(null)}>
            Cancel
          </Button>,
          <Button
            key="save"
            type="primary"
            loading={saving}
            onClick={() => void handleSaveRoles()}
            style={{ background: brand.purple, borderColor: brand.purple }}
          >
            Save
          </Button>,
        ]}
      >
        <div style={{ padding: '8px 0 4px' }}>
          <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
            Select all roles this user should have. Changes take effect on their
            next login.
          </Text>
          <Checkbox.Group
            value={selectedRoles}
            onChange={(v) => setSelectedRoles(v as string[])}
            style={{ display: 'flex', flexDirection: 'column', gap: 12 }}
          >
            {ALL_ROLES.map((role) => (
              <Checkbox key={role} value={role}>
                <Tag
                  style={{
                    background: `${ROLE_COLORS[role] ?? brand.purple}18`,
                    borderColor: `${ROLE_COLORS[role] ?? brand.purple}40`,
                    color: ROLE_COLORS[role] ?? brand.purple,
                    fontWeight: 500,
                  }}
                >
                  {role}
                </Tag>
                <Text type="secondary" style={{ fontSize: 12, marginLeft: 4 }}>
                  {ROLE_DESCRIPTIONS[role]}
                </Text>
              </Checkbox>
            ))}
          </Checkbox.Group>
        </div>
      </Modal>

      {/*
        Shown once. Keycloak will not return this value again, so the dialog
        says so plainly rather than letting an administrator close it and come
        looking for the password later.
      */}
      <Modal
        title={
          <Space>
            <KeyOutlined style={{ color: brand.purple }} />
            <span>Temporary password — {pwUser?.username}</span>
          </Space>
        }
        open={pwValue !== null}
        onCancel={() => {
          setPwValue(null)
          setPwUser(null)
        }}
        footer={[
          <Button
            key="copy"
            onClick={() => {
              if (pwValue) {
                void navigator.clipboard.writeText(pwValue)
                void message.success('Copied')
              }
            }}
          >
            Copy
          </Button>,
          <Button
            key="done"
            type="primary"
            onClick={() => {
              setPwValue(null)
              setPwUser(null)
            }}
          >
            Done
          </Button>,
        ]}
      >
        <Typography.Paragraph>
          Give this to {pwUser?.firstName} {pwUser?.lastName}. They must change it
          the first time they sign in, so it is not the password they end up with.
        </Typography.Paragraph>
        <Typography.Paragraph
          code
          copyable={{ text: pwValue ?? '' }}
          style={{ fontSize: 18, textAlign: 'center', padding: '12px 0' }}
        >
          {pwValue}
        </Typography.Paragraph>
        <Text type="secondary">
          This is shown once — closing this dialog discards it. If it is lost,
          set another one; the old password stops working.
        </Text>
      </Modal>
    </div>
  )
}

const ROLE_DESCRIPTIONS: Record<string, string> = {
  SYSTEM_ADMIN: 'Full platform access + user management',
  HR_ADMIN: 'Approve & manage all HR processes',
  HR_SPECIALIST: 'Submit requests + first-line approvals',
  AUDITOR: 'Read-only access to all modules',
  DEPARTMENT_MANAGER: 'Approve team requests + view team data',
  EMPLOYEE: 'Self-service only (My Workspace)',
}
