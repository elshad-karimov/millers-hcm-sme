// M243 — Position Lifecycle UI (Phase A of Position Management spec).
//
// Single reusable component used by PositionFormPage and PositionsPage row
// action menus. Renders:
//
//   1. Current status as a coloured pill
//   2. Action menu — only legal next-states are enabled
//   3. Freeze modal — captures reason + optional scheduled unfreeze date
//   4. Reason modal — captures reason for reject / under-review / close
//   5. Read-only history timeline (collapsible)
//
// Per the standing "develop once, use everywhere" rule: callers pass a
// Position and an onChange callback; everything else (validation,
// label translation, error toast) is encapsulated here. Adding a new
// lifecycle action elsewhere = just import this component.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  DatePicker,
  Dropdown,
  Form,
  Input,
  Modal,
  Space,
  Tag,
  Timeline,
  Tooltip,
  Typography,
} from 'antd'
import { DownOutlined, HistoryOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  POSITION_STATUS_COLOR,
  POSITION_STATUS_NEXT,
  positionsApi,
  type LifecycleActionRequest,
  type Position,
  type PositionLifecycleEvent,
  type PositionStatus,
} from '../api/positions'
import type { ReasonCategory } from '../api/reasonMaster'
import { ReasonSelect } from './ReasonSelect'

/**
 * M259 — Map a lifecycle action to its reason-master category.
 *
 * <p>FREEZE / UNDER_REVIEW / REJECT all use FREEZE category (review and
 * rejection are both holds, semantically aligned with freeze reasons).
 * CLOSE uses CLOSURE. Everything else uses FREEZE as a safe default
 * since those actions don't actually surface the reason UI.
 */
function reasonCategoryFor(
  actionId: string,
): ReasonCategory {
  if (actionId === 'close') return 'CLOSURE'
  return 'FREEZE'
}

/**
 * Human label for every state + the action that gets you there. Both
 * indexed by the target status so the menu is one mapping.
 */
const ACTION_BY_STATE: Record<
  PositionStatus,
  {
    actionId:
      | 'submit'
      | 'approve'
      | 'reject'
      | 'activate'
      | 'freeze'
      | 'unfreeze'
      | 'under-review'
      | 'finish-review'
      | 'close'
      | 'archive'
    label: string
    /** Whether the modal must collect a reason. */
    requiresReason: boolean
    /** Whether the freeze modal (with scheduledUnfreezeDate) should open. */
    isFreeze?: boolean
  }
> = {
  DRAFT: { actionId: 'reject', label: 'Send back to draft', requiresReason: true },
  PENDING_APPROVAL: { actionId: 'submit', label: 'Submit for approval', requiresReason: false },
  APPROVED: { actionId: 'approve', label: 'Approve', requiresReason: false },
  ACTIVE: { actionId: 'activate', label: 'Activate', requiresReason: false },
  FROZEN: { actionId: 'freeze', label: 'Freeze', requiresReason: true, isFreeze: true },
  UNDER_REVIEW: { actionId: 'under-review', label: 'Mark under review', requiresReason: true },
  CLOSED: { actionId: 'close', label: 'Close', requiresReason: true },
  ARCHIVED: { actionId: 'archive', label: 'Archive', requiresReason: false },
}

/**
 * For some transitions the destination state doesn't uniquely identify
 * the action. The (from, to) pair does. These overrides resolve those
 * ambiguous cases so the right backend endpoint is hit.
 */
function resolveAction(
  from: PositionStatus,
  to: PositionStatus,
): typeof ACTION_BY_STATE[PositionStatus] {
  // FROZEN → ACTIVE is unfreeze (not "activate", which would 400).
  if (from === 'FROZEN' && to === 'ACTIVE') {
    return { actionId: 'unfreeze', label: 'Unfreeze', requiresReason: false }
  }
  // UNDER_REVIEW → ACTIVE is finish-review.
  if (from === 'UNDER_REVIEW' && to === 'ACTIVE') {
    return { actionId: 'finish-review', label: 'Finish review', requiresReason: false }
  }
  return ACTION_BY_STATE[to]
}

interface Props {
  position: Position
  /**
   * Fired after a successful transition with the updated Position.
   * Caller decides whether to re-fetch the list or splice the row in.
   */
  onChange: (updated: Position) => void
  /** When false, the action menu is hidden (read-only mode). */
  canAct?: boolean
}

export function PositionLifecyclePanel({ position, onChange, canAct = true }: Props) {
  const { message } = AntdApp.useApp()
  const [pending, setPending] = useState<{
    target: PositionStatus
    isFreeze: boolean
    requiresReason: boolean
    label: string
    actionId: typeof ACTION_BY_STATE[PositionStatus]['actionId']
  } | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [history, setHistory] = useState<PositionLifecycleEvent[] | null>(null)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [form] = Form.useForm<LifecycleActionRequest & { scheduledUnfreezeDate?: dayjs.Dayjs }>()

  const nextStates = POSITION_STATUS_NEXT[position.status] ?? []

  // Lazy-load history the first time the user expands the timeline.
  useEffect(() => {
    if (!historyOpen || history !== null) return
    positionsApi.lifecycle.history(position.id)
      .then(setHistory)
      .catch((err) =>
        message.warning(err?.response?.data?.message ?? 'Could not load history'),
      )
  }, [historyOpen, history, position.id, message])

  const menuItems = useMemo(
    () =>
      nextStates.map((target) => {
        const meta = resolveAction(position.status, target)
        return {
          key: target,
          label: meta.label,
        }
      }),
    [nextStates, position.status],
  )

  const startTransition = (target: PositionStatus) => {
    const meta = resolveAction(position.status, target)
    // Fast path: no reason / no freeze date → fire immediately, no modal.
    if (!meta.requiresReason && !meta.isFreeze) {
      void runAction(target, meta.actionId, {})
      return
    }
    setPending({
      target,
      isFreeze: !!meta.isFreeze,
      requiresReason: meta.requiresReason,
      label: meta.label,
      actionId: meta.actionId,
    })
    form.resetFields()
  }

  const runAction = async (
    _target: PositionStatus,
    actionId: typeof ACTION_BY_STATE[PositionStatus]['actionId'],
    body: LifecycleActionRequest,
  ) => {
    setSubmitting(true)
    try {
      const updated = await positionsApi.lifecycle.act(position.id, actionId, body)
      onChange(updated)
      // Reset history so the timeline re-fetches on next open and shows
      // the new event.
      setHistory(null)
      message.success(`Position ${updated.status.replace(/_/g, ' ').toLowerCase()}`)
      setPending(null)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not change status')
    } finally {
      setSubmitting(false)
    }
  }

  const onModalOk = async () => {
    if (!pending) return
    const values = await form.validateFields()
    await runAction(pending.target, pending.actionId, {
      reason: values.reason,
      comments: values.comments,
      scheduledUnfreezeDate: values.scheduledUnfreezeDate
        ? values.scheduledUnfreezeDate.format('YYYY-MM-DD')
        : undefined,
    })
  }

  return (
    <Space direction="vertical" size="small" style={{ width: '100%' }}>
      <Space size="small" wrap>
        <Tag color={POSITION_STATUS_COLOR[position.status]} style={{ fontWeight: 600 }}>
          {position.status.replace(/_/g, ' ')}
        </Tag>

        {/* Lifecycle context — freeze reason / closure reason / approval breadcrumb. */}
        {position.status === 'FROZEN' && position.freezeReason && (
          <Tooltip
            title={
              <>
                <div>Reason: {position.freezeReason}</div>
                {position.frozenBy && <div>By: {position.frozenBy}</div>}
                {position.scheduledUnfreezeDate && (
                  <div>Scheduled unfreeze: {position.scheduledUnfreezeDate}</div>
                )}
              </>
            }
          >
            <Typography.Text type="secondary" style={{ cursor: 'help' }}>
              ❄️ {position.freezeReason}
            </Typography.Text>
          </Tooltip>
        )}
        {position.status === 'CLOSED' && position.closureReason && (
          <Typography.Text type="secondary">🚫 {position.closureReason}</Typography.Text>
        )}
        {position.status === 'UNDER_REVIEW' && position.reviewReason && (
          <Typography.Text type="secondary">🔍 {position.reviewReason}</Typography.Text>
        )}

        {canAct && nextStates.length > 0 && (
          <Dropdown
            menu={{
              items: menuItems,
              onClick: (info) => startTransition(info.key as PositionStatus),
            }}
            disabled={submitting}
          >
            <Button size="small">
              Change status <DownOutlined />
            </Button>
          </Dropdown>
        )}

        <Button
          size="small"
          type="link"
          icon={<HistoryOutlined />}
          onClick={() => setHistoryOpen((v) => !v)}
        >
          {historyOpen ? 'Hide history' : 'Show history'}
        </Button>
      </Space>

      {historyOpen && (
        <Timeline
          items={(history ?? []).map((e) => ({
            color: POSITION_STATUS_COLOR[e.toStatus],
            children: (
              <Space direction="vertical" size={2}>
                <div>
                  <strong>{e.fromStatus ?? '∅'}</strong> → <strong>{e.toStatus}</strong>
                </div>
                {e.reason && <div>Reason: {e.reason}</div>}
                {e.comments && <div>Notes: {e.comments}</div>}
                {e.scheduledUnfreezeDate && (
                  <div>Scheduled unfreeze: {e.scheduledUnfreezeDate}</div>
                )}
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {e.actor ?? 'system'} · {dayjs(e.occurredAt).format('YYYY-MM-DD HH:mm')}
                </Typography.Text>
              </Space>
            ),
          }))}
          style={{ marginTop: 8 }}
        />
      )}

      {/* Modal for transitions that need a reason / freeze date. */}
      <Modal
        title={pending ? `${pending.label} — ${position.code}` : ''}
        open={!!pending}
        onOk={onModalOk}
        onCancel={() => setPending(null)}
        confirmLoading={submitting}
        okText="Confirm"
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          {pending?.requiresReason && (
            <Form.Item
              name="reason"
              label="Reason"
              rules={[{ required: true, message: 'A reason is required for this action.' }]}
              tooltip="Pick from the master list or type a custom reason"
            >
              {/* M259 — backed by §22 reason master. Maps the action
                  to the appropriate category; AutoComplete allows
                  freeform fallback. */}
              <ReasonSelect
                category={reasonCategoryFor(pending.actionId)}
                placeholder="e.g. Budget freeze 2026 Q1"
              />
            </Form.Item>
          )}
          {pending?.isFreeze && (
            <Form.Item
              name="scheduledUnfreezeDate"
              label="Scheduled unfreeze date (optional)"
              tooltip="If set, the position will auto-unfreeze on this date (Phase B)."
            >
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          )}
          <Form.Item name="comments" label="Comments (optional)">
            <Input.TextArea rows={2} maxLength={2000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
