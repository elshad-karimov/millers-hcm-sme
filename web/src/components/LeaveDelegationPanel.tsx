/**
 * Reusable delegation panel — embed in any leave-request detail view.
 * Shows existing delegations and lets HR/managers add, accept, decline, or revoke.
 */
import { useEffect, useState } from 'react'
import {
  Button,
  Form,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  App as AntdApp,
} from 'antd'
import { PlusOutlined, CheckOutlined, CloseOutlined, StopOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { leaveApi, type LeaveDelegation } from '../api/leave'
import { EmployeePicker } from './EmployeePicker'

const STATUS_COLOR: Record<string, string> = {
  PENDING: 'orange',
  ACCEPTED: 'green',
  DECLINED: 'red',
  REVOKED: 'default',
}

interface Props {
  leaveRequestId: string
}

export function LeaveDelegationPanel({ leaveRequestId }: Props) {
  const { message } = AntdApp.useApp()
  const [items, setItems] = useState<LeaveDelegation[]>([])
  const [addModal, setAddModal] = useState(false)
  const [responseModal, setResponseModal] = useState<{ id: string; action: 'accept' | 'decline' } | null>(null)
  const [saving, setSaving] = useState(false)
  const [delegateId, setDelegateId] = useState<string | undefined>()
  const [scope, setScope] = useState('')
  const [notes, setNotes] = useState('')

  const reload = () =>
    leaveApi.delegations(leaveRequestId).then(setItems)

  useEffect(() => {
    reload()
  }, [leaveRequestId])

  const doAdd = async () => {
    if (!delegateId) return
    setSaving(true)
    try {
      await leaveApi.createDelegation(leaveRequestId, { delegateId, delegationScope: scope || undefined })
      await reload()
      setAddModal(false)
      setDelegateId(undefined)
      setScope('')
      message.success('Delegation sent')
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed')
    } finally {
      setSaving(false)
    }
  }

  const doRespond = async () => {
    if (!responseModal) return
    setSaving(true)
    try {
      if (responseModal.action === 'accept') {
        await leaveApi.acceptDelegation(responseModal.id, notes || undefined)
        message.success('Delegation accepted')
      } else {
        await leaveApi.declineDelegation(responseModal.id, notes || undefined)
        message.success('Delegation declined')
      }
      await reload()
      setResponseModal(null)
      setNotes('')
    } catch {
      message.error('Failed')
    } finally {
      setSaving(false)
    }
  }

  const doRevoke = async (id: string) => {
    try {
      await leaveApi.revokeDelegation(id)
      await reload()
      message.success('Delegation revoked')
    } catch {
      message.error('Failed to revoke')
    }
  }

  const columns: ColumnsType<LeaveDelegation> = [
    { title: 'Delegate', dataIndex: 'delegateName' },
    { title: 'Scope', dataIndex: 'delegationScope' },
    {
      title: 'Status',
      render: (_, r) => <Tag color={STATUS_COLOR[r.status]}>{r.status}</Tag>,
    },
    { title: 'Notes', dataIndex: 'delegateNotes' },
    {
      title: 'Actions',
      render: (_, r) =>
        r.status === 'PENDING' ? (
          <Space size="small">
            <Button size="small" type="primary" icon={<CheckOutlined />}
              onClick={() => setResponseModal({ id: r.id, action: 'accept' })}>
              Accept
            </Button>
            <Button size="small" danger icon={<CloseOutlined />}
              onClick={() => setResponseModal({ id: r.id, action: 'decline' })}>
              Decline
            </Button>
            <Button size="small" icon={<StopOutlined />} onClick={() => doRevoke(r.id)}>
              Revoke
            </Button>
          </Space>
        ) : r.status === 'ACCEPTED' ? (
          <Button size="small" icon={<StopOutlined />} onClick={() => doRevoke(r.id)}>
            Revoke
          </Button>
        ) : null,
    },
  ]

  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <strong>Coverage Delegations</strong>
        <Button size="small" icon={<PlusOutlined />} onClick={() => setAddModal(true)}>
          Add Delegate
        </Button>
      </div>

      <Table rowKey="id" columns={columns} dataSource={items} size="small" pagination={false} />

      {/* Add modal */}
      <Modal
        title="Assign coverage delegate"
        open={addModal}
        onCancel={() => setAddModal(false)}
        onOk={doAdd}
        confirmLoading={saving}
        okText="Send"
      >
        <Form layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Delegate employee" required>
            <EmployeePicker
              value={delegateId}
              onChange={setDelegateId}
              placeholder="Select employee"
            />
          </Form.Item>
          <Form.Item label="Scope / responsibilities (optional)">
            <Input.TextArea
              rows={2}
              value={scope}
              onChange={(e) => setScope(e.target.value)}
              placeholder="e.g. Handle purchase approvals, attend daily standups"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Accept / Decline modal */}
      <Modal
        title={responseModal?.action === 'accept' ? 'Accept delegation' : 'Decline delegation'}
        open={!!responseModal}
        onCancel={() => setResponseModal(null)}
        onOk={doRespond}
        confirmLoading={saving}
        okText={responseModal?.action === 'accept' ? 'Accept' : 'Decline'}
        okButtonProps={{ danger: responseModal?.action === 'decline' }}
      >
        <Form layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Notes (optional)">
            <Input.TextArea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
