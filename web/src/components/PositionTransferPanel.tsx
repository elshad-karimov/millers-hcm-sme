import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  positionTransferApi,
  TRANSFER_STATUS_COLOR,
  type InitiateTransferRequest,
  type PositionTransfer,
  type TransferStatus,
} from '../api/positionTransfer'
import { ReasonSelect } from './ReasonSelect'

const { Text } = Typography

/**
 * M260 — Position transfer workflow panel (PRD §40).
 *
 * <p>Embedded in PositionFormPage on edit. Lists every transfer (most
 * recent first) and lets the operator drive the state machine through
 * per-row action buttons:
 *   DRAFT → Submit
 *   PENDING_APPROVAL → Approve / Reject
 *   APPROVED → Complete (applies the new org unit / cost centre / location)
 *   any non-terminal → Cancel
 *
 * <p>The "+ New transfer" modal collects target fields + reason +
 * effective date. Source side is snapshotted server-side from the
 * current Position record.
 */
export function PositionTransferPanel({ positionId }: { positionId: string }) {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<PositionTransfer[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<{
    toOrgUnitId?: string
    toCostCentre?: string
    toLocation?: string
    transferReason?: string
    notes?: string
    effectiveDate: dayjs.Dayjs
  }>()

  const load = () => {
    setLoading(true)
    positionTransferApi
      .list(positionId)
      .then(setRows)
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load transfers'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [positionId])

  const onInitiate = async () => {
    try {
      const v = await form.validateFields()
      setSubmitting(true)
      const req: InitiateTransferRequest = {
        positionId,
        toOrgUnitId: v.toOrgUnitId || undefined,
        toCostCentre: v.toCostCentre || undefined,
        toLocation: v.toLocation || undefined,
        transferReason: v.transferReason || undefined,
        notes: v.notes || undefined,
        effectiveDate: v.effectiveDate.format('YYYY-MM-DD'),
      }
      await positionTransferApi.initiate(req)
      message.success('Transfer drafted')
      form.resetFields()
      setModalOpen(false)
      load()
    } catch (e: any) {
      if (e?.errorFields) return // validation error — shown inline
      message.error(e?.response?.data?.message ?? 'Failed to draft transfer')
    } finally {
      setSubmitting(false)
    }
  }

  const action = async (
    fn: () => Promise<unknown>,
    okMessage: string,
  ) => {
    try {
      await fn()
      message.success(okMessage)
      load()
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Failed')
    }
  }

  const columns: ColumnsType<PositionTransfer> = [
    {
      title: 'Effective',
      dataIndex: 'effectiveDate',
      width: 110,
    },
    {
      title: 'From → To',
      key: 'route',
      render: (_: unknown, r: PositionTransfer) => (
        <Space direction="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>
            {r.fromOrgUnitLabel ?? r.fromCostCentre ?? r.fromLocation ?? '—'} →{' '}
            <strong>
              {r.toOrgUnitLabel ?? r.toCostCentre ?? r.toLocation ?? '—'}
            </strong>
          </Text>
          {r.transferReason && (
            <Text type="secondary" style={{ fontSize: 11 }}>
              {r.transferReason}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 140,
      render: (s: TransferStatus) => <Tag color={TRANSFER_STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 280,
      render: (_: unknown, r: PositionTransfer) => (
        <Space size={4} wrap>
          {r.status === 'DRAFT' && (
            <Button
              size="small"
              type="primary"
              onClick={() =>
                action(() => positionTransferApi.submit(r.id), 'Submitted for approval')
              }
            >
              Submit
            </Button>
          )}
          {r.status === 'PENDING_APPROVAL' && (
            <>
              <Button
                size="small"
                type="primary"
                onClick={() =>
                  action(() => positionTransferApi.approve(r.id), 'Approved')
                }
              >
                Approve
              </Button>
              <Popconfirm
                title="Reject this transfer?"
                onConfirm={() =>
                  action(() => positionTransferApi.reject(r.id), 'Rejected')
                }
              >
                <Button size="small" danger>
                  Reject
                </Button>
              </Popconfirm>
            </>
          )}
          {r.status === 'APPROVED' && (
            <Popconfirm
              title="Apply this transfer? The position will move now."
              onConfirm={() =>
                action(
                  () => positionTransferApi.complete(r.id),
                  'Transfer applied — position moved',
                )
              }
            >
              <Button size="small" type="primary">
                ✓ Complete
              </Button>
            </Popconfirm>
          )}
          {(r.status === 'DRAFT' ||
            r.status === 'PENDING_APPROVAL' ||
            r.status === 'APPROVED') && (
            <Popconfirm
              title="Cancel this transfer?"
              onConfirm={() =>
                action(() => positionTransferApi.cancel(r.id), 'Cancelled')
              }
            >
              <Button size="small">Cancel</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" onClick={() => setModalOpen(true)}>
          + New transfer
        </Button>
        <Text type="secondary" style={{ fontSize: 12 }}>
          Move this position to a new org unit, cost centre, or location.
        </Text>
      </Space>
      <Table<PositionTransfer>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        locale={{ emptyText: 'No transfers yet' }}
        pagination={false}
      />

      <Modal
        title="📦 New position transfer (PRD §40)"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          form.resetFields()
        }}
        onOk={onInitiate}
        confirmLoading={submitting}
        okText="Save draft"
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          preserve={false}
          initialValues={{ effectiveDate: dayjs() }}
        >
          <Form.Item
            name="toOrgUnitId"
            label="Target org unit ID"
            tooltip="UUID of the destination org unit (leave blank to keep current)"
          >
            <Input placeholder="UUID" />
          </Form.Item>
          <Form.Item name="toCostCentre" label="Target cost centre">
            <Input placeholder="e.g. CC-ENG" maxLength={64} />
          </Form.Item>
          <Form.Item name="toLocation" label="Target location">
            <Input placeholder="e.g. Baku HQ" maxLength={160} />
          </Form.Item>
          <Form.Item
            name="transferReason"
            label="Transfer reason"
            tooltip="Pick from the §22 reason master or type a custom reason"
          >
            <ReasonSelect category="VACANCY" placeholder="Why is this transfer happening?" />
          </Form.Item>
          <Form.Item
            name="effectiveDate"
            label="Effective date"
            rules={[{ required: true, message: 'Pick an effective date' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes (optional)">
            <Input.TextArea rows={2} maxLength={2000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
