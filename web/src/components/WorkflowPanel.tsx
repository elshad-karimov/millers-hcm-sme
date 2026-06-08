import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Empty,
  Input,
  Modal,
  Space,
  Spin,
  Tag,
  Timeline,
  Typography,
  App as AntdApp,
} from 'antd'
import {
  workflowApi,
  type WorkflowAction,
  type WorkflowActionType,
  type WorkflowInstance,
  type WorkflowStatus,
} from '../api/workflow'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const STATUS_COLOR: Record<WorkflowStatus, string> = {
  PENDING: 'gold',
  APPROVED: 'green',
  AUTO_APPROVED: 'green',
  REJECTED: 'red',
  RETURNED: 'orange',
  CANCELLED: 'default',
}

const ACTION_COLOR: Record<WorkflowActionType, string> = {
  START: 'blue',
  APPROVE: 'green',
  AUTO_APPROVE: 'green',
  REJECT: 'red',
  RETURN: 'orange',
  RESUBMIT: 'blue',
  COMMENT: 'default',
  CANCEL: 'default',
  DELEGATE: 'purple',
  ATTACH_DOCUMENT: 'cyan',
}

interface Props {
  module: string
  entity: string
  subjectId: string
  /** Re-fetched after a terminal action so the parent can refresh its own state. */
  onChanged?: () => void
}

export function WorkflowPanel({ module, entity, subjectId, onChanged }: Props) {
  const { user, hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const [instances, setInstances] = useState<WorkflowInstance[]>([])
  const [history, setHistory] = useState<Record<string, WorkflowAction[]>>({})
  const [loading, setLoading] = useState(false)
  const [actModal, setActModal] = useState<{
    instance: WorkflowInstance
    action: WorkflowActionType
  } | null>(null)
  const [resubmitModal, setResubmitModal] = useState<WorkflowInstance | null>(null)
  const [resubmitComment, setResubmitComment] = useState('')
  const [comment, setComment] = useState('')

  const load = () => {
    setLoading(true)
    workflowApi
      .bySubject(module, entity, subjectId)
      .then(async (list) => {
        setInstances(list)
        const histories: Record<string, WorkflowAction[]> = {}
        await Promise.all(
          list.map(async (i) => {
            histories[i.id] = await workflowApi.history(i.id)
          }),
        )
        setHistory(histories)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [module, entity, subjectId])

  if (loading) {
    return (
      <Card type="inner" title="Workflow">
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
        </div>
      </Card>
    )
  }
  if (instances.length === 0) {
    return (
      <Card type="inner" title="Workflow">
        <Empty description="No workflow has been started for this item" />
      </Card>
    )
  }

  const submit = async (instance: WorkflowInstance, action: WorkflowActionType) => {
    if ((action === 'REJECT' || action === 'RETURN' || action === 'COMMENT') && !comment.trim()) {
      message.warning('A comment is required for this action')
      return
    }
    try {
      await workflowApi.act(instance.id, action, comment || undefined)
      message.success(`${action} recorded`)
      setActModal(null)
      setComment('')
      load()
      onChanged?.()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Action failed',
      )
    }
  }

  return (
    <>
      {instances.map((i) => {
        const canActAsApprover =
          i.status === 'PENDING' &&
          !!i.currentStepRole &&
          (hasRole(i.currentStepRole.replace('ROLE_', '')) || hasRole(...RoleSets.SYS_ADMIN_ONLY)) &&
          user?.username !== i.initiatedBy
        const canCancel = i.status === 'PENDING' && (user?.username === i.initiatedBy || hasRole(...RoleSets.SYS_ADMIN_ONLY))
        const canComment = i.status === 'PENDING'
        const canResubmit = i.status === 'RETURNED' && user?.username === i.initiatedBy && (i.resubmitCount ?? 0) < 10
        const items = (history[i.id] ?? []).map((a) => ({
          color: ACTION_COLOR[a.action],
          children: (
            <Space direction="vertical" size={0}>
              <Space>
                <Tag color={ACTION_COLOR[a.action]}>{a.action}</Tag>
                <Typography.Text strong>{a.actor}</Typography.Text>
                {a.stepName && <Typography.Text type="secondary">@ {a.stepName}</Typography.Text>}
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {new Date(a.createdAt).toLocaleString()}
                </Typography.Text>
              </Space>
              {a.comment && <Typography.Text italic>“{a.comment}”</Typography.Text>}
            </Space>
          ),
        }))

        return (
          <Card
            key={i.id}
            type="inner"
            style={{ marginBottom: 12 }}
            title={
              <Space>
                <Typography.Text strong>{i.definitionCode}</Typography.Text>
                <Tag color={STATUS_COLOR[i.status]}>{i.status}</Tag>
                {i.status === 'PENDING' && i.currentStepRole && (
                  <Tag>waiting on {i.currentStepRole.replace('ROLE_', '')}</Tag>
                )}
              </Space>
            }
            extra={
              <Space>
                {canActAsApprover && (
                  <>
                    <Button onClick={() => setActModal({ instance: i, action: 'RETURN' })}>
                      Return
                    </Button>
                    <Button danger onClick={() => setActModal({ instance: i, action: 'REJECT' })}>
                      Reject
                    </Button>
                    <Button
                      type="primary"
                      onClick={() => setActModal({ instance: i, action: 'APPROVE' })}
                    >
                      Approve
                    </Button>
                  </>
                )}
                {canComment && (
                  <Button onClick={() => setActModal({ instance: i, action: 'COMMENT' })}>
                    Comment
                  </Button>
                )}
                {canCancel && (
                  <Button onClick={() => setActModal({ instance: i, action: 'CANCEL' })}>
                    Cancel
                  </Button>
                )}
                {canResubmit && (
                  <Button type="primary" onClick={() => setResubmitModal(i)}>
                    Resubmit
                  </Button>
                )}
              </Space>
            }
          >
            <Space direction="vertical" size={4} style={{ marginBottom: 12 }}>
              <Typography.Text>{i.title}</Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Initiated by <b>{i.initiatedBy}</b> on{' '}
                {new Date(i.initiatedAt).toLocaleString()}
                {i.completedAt && (
                  <>
                    {' · '}closed {new Date(i.completedAt).toLocaleString()}
                  </>
                )}
              </Typography.Text>
            </Space>
            <Timeline items={items} />
          </Card>
        )
      })}

      <Modal
        open={!!actModal}
        title={actModal ? `${actModal.action} workflow` : ''}
        onCancel={() => {
          setActModal(null)
          setComment('')
        }}
        onOk={() => actModal && submit(actModal.instance, actModal.action)}
        okText={actModal?.action.toLocaleLowerCase()}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text type="secondary">
            {actModal?.action === 'APPROVE'
              ? 'Comment is optional.'
              : 'A comment is required for this action.'}
          </Typography.Text>
          <Input.TextArea
            rows={4}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Add your comment"
          />
        </Space>
      </Modal>

      <Modal
        open={!!resubmitModal}
        title="Resubmit for approval"
        onCancel={() => {
          setResubmitModal(null)
          setResubmitComment('')
        }}
        onOk={async () => {
          if (!resubmitModal) return
          try {
            await workflowApi.resubmit(resubmitModal.id, resubmitComment || undefined)
            message.success('Request re-submitted')
            setResubmitModal(null)
            setResubmitComment('')
            load()
            onChanged?.()
          } catch (err) {
            message.error(
              (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
                'Resubmit failed',
            )
          }
        }}
        okText="Resubmit"
        okButtonProps={{ type: 'primary' }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text type="secondary">
            The request will restart from step 1 of the approval chain. Add an optional note
            explaining what was corrected.
          </Typography.Text>
          <Input.TextArea
            rows={3}
            value={resubmitComment}
            onChange={(e) => setResubmitComment(e.target.value)}
            placeholder="What was corrected? (optional)"
          />
        </Space>
      </Modal>
    </>
  )
}
