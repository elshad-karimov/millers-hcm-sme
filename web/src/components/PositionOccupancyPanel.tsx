// M246 — Position Occupancy + Replacement Panel.
//
// Reusable: drops into PositionFormPage to show the seat's full
// history of occupants (active + ended), with controls to assign a
// new occupant, end an active one, or open a replacement workflow.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  OCCUPANCY_TYPE_COLOR,
  OCCUPANCY_TYPE_LABEL,
  positionOccupancyApi,
  positionReplacementApi,
  REPLACEMENT_ACTION_LABEL,
  REPLACEMENT_STATUS_COLOR,
  REPLACEMENT_STATUS_LABEL,
  type OccupancyRequest,
  type OccupancyType,
  type PositionOccupancy,
  type PositionReplacement,
  type ReplacementAction,
  type ReplacementRequest,
} from '../api/positionOccupancy'
import { employeesApi, type Employee } from '../api/employees'
import { EmployeePicker } from './EmployeePicker'
import {
  GRANT_STATUS_COLOR,
  GRANT_STATUS_LABEL,
  positionProfileGrantApi,
  type ProfileGrant,
} from '../api/positionProfileGrant'
import {
  PROFILE_ITEM_TYPE_COLOR,
  PROFILE_ITEM_TYPE_ICON,
  PROFILE_ITEM_TYPE_LABEL,
} from '../api/positionProfile'

interface Props {
  positionId: string
  canEdit?: boolean
}

const OCCUPANCY_TYPE_OPTIONS = (
  ['PRIMARY', 'ACTING', 'TEMPORARY', 'SECONDARY', 'SECONDMENT', 'INTERN', 'CONTRACTOR'] as OccupancyType[]
).map((t) => ({ value: t, label: OCCUPANCY_TYPE_LABEL[t] }))

const REPLACEMENT_ACTION_OPTIONS = (
  ['OPEN_RECRUITMENT', 'INTERNAL_TRANSFER', 'ACTING', 'FREEZE', 'CLOSE'] as ReplacementAction[]
).map((a) => ({ value: a, label: REPLACEMENT_ACTION_LABEL[a] }))

const END_REASON_OPTIONS = [
  'RESIGNATION',
  'TERMINATION',
  'RETIREMENT',
  'TRANSFER',
  'PROMOTION',
  'SECONDED_OUT',
  'INTERIM_END',
  'CONTRACT_END',
].map((r) => ({ value: r, label: r.replace(/_/g, ' ') }))

export function PositionOccupancyPanel({ positionId, canEdit = true }: Props) {
  const { message } = AntdApp.useApp()
  const [occupancies, setOccupancies] = useState<PositionOccupancy[]>([])
  const [replacements, setReplacements] = useState<PositionReplacement[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(true)
  const [assignModal, setAssignModal] = useState(false)
  const [endModal, setEndModal] = useState<PositionOccupancy | null>(null)
  const [replacementModal, setReplacementModal] = useState<PositionOccupancy | null>(null)
  // M250 — Phase F.2: grant drawer for a specific occupancy.
  const [grantsOpen, setGrantsOpen] = useState<PositionOccupancy | null>(null)
  const [grants, setGrants] = useState<ProfileGrant[]>([])
  const [grantsLoading, setGrantsLoading] = useState(false)
  // Batched count of PENDING grants per occupancy id — populated alongside
  // the occupancy refresh so the row badge is always current.
  const [pendingCounts, setPendingCounts] = useState<Record<string, number>>({})

  type AssignFormValues = {
    employeeId: string
    occupancyType: OccupancyType
    fteAllocation: number
    startDate: dayjs.Dayjs
    actingAllowance?: number
    actingAllowanceCurrency?: string
    homePositionId?: string
    notes?: string
  }
  type EndFormValues = {
    endDate: dayjs.Dayjs
    reason?: string
    notes?: string
  }
  type ReplacementFormValues = {
    reason: string
    lastWorkingDay: dayjs.Dayjs
    action: ReplacementAction
    replacementEmployeeId?: string
    replacementStartDate?: dayjs.Dayjs
    handoverOverlapDays?: number
    notes?: string
  }
  const [assignForm] = Form.useForm<AssignFormValues>()
  const [endForm] = Form.useForm<EndFormValues>()
  const [replacementForm] = Form.useForm<ReplacementFormValues>()

  const refresh = () => {
    setLoading(true)
    positionOccupancyApi.forPosition(positionId).then((rows) => {
      setOccupancies(rows)
      // After loading occupancies, fetch each one's grants in parallel
      // and tally PENDING counts. This is a small N (rarely > 10 rows
      // per position) so a per-row GET is fine without an aggregate
      // endpoint.
      const counts: Record<string, number> = {}
      Promise.all(rows.map((o) =>
        positionProfileGrantApi.forOccupancy(o.id).then((gs) => {
          counts[o.id] = gs.filter((g) => g.status === 'PENDING').length
        }).catch(() => {
          counts[o.id] = 0
        }),
      )).then(() => setPendingCounts(counts))
    }).catch(() => setOccupancies([]))
    positionReplacementApi.forPosition(positionId).then(setReplacements).catch(() => setReplacements([]))
    setLoading(false)
  }

  // ── Grants drawer ────────────────────────────────────────────────

  const openGrants = (row: PositionOccupancy) => {
    setGrantsOpen(row)
    setGrantsLoading(true)
    positionProfileGrantApi.forOccupancy(row.id)
      .then(setGrants)
      .catch(() => setGrants([]))
      .finally(() => setGrantsLoading(false))
  }
  const refreshGrants = () => {
    if (!grantsOpen) return
    setGrantsLoading(true)
    positionProfileGrantApi.forOccupancy(grantsOpen.id)
      .then(setGrants)
      .catch(() => setGrants([]))
      .finally(() => setGrantsLoading(false))
    // Also bump the badge counter on the row.
    refresh()
  }
  const markGrantActive = async (id: string) => {
    try {
      await positionProfileGrantApi.markActive(id)
      message.success('Marked active')
      refreshGrants()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Failed')
    }
  }
  const revokeGrant = async (id: string) => {
    try {
      await positionProfileGrantApi.revoke(id, 'Manually revoked from occupancy panel')
      message.success('Revoked')
      refreshGrants()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Failed')
    }
  }

  useEffect(refresh, [positionId])

  useEffect(() => {
    employeesApi.list({ size: 500 })
      .then((r) => setEmployees(r.content))
      .catch(() => setEmployees([]))
  }, [])

  const employeeLabel = (id?: string | null) => {
    if (!id) return '—'
    const e = employees.find((x) => x.id === id)
    return e
      ? `${e.employeeNo} · ${e.firstName} ${e.lastName}`
      : id.slice(0, 8) + '…'
  }

  const active = useMemo(() => occupancies.filter((o) => !o.endDate), [occupancies])
  const totalActiveFte = active.reduce((sum, o) => sum + Number(o.fteAllocation), 0)

  // ── Handlers ─────────────────────────────────────────────────────

  const openAssign = () => {
    assignForm.resetFields()
    assignForm.setFieldsValue({
      occupancyType: 'PRIMARY',
      fteAllocation: 1.0,
      startDate: dayjs(),
      actingAllowanceCurrency: 'AZN',
    })
    setAssignModal(true)
  }

  const onAssign = async () => {
    const v = await assignForm.validateFields()
    const body: OccupancyRequest = {
      positionId,
      employeeId: v.employeeId,
      occupancyType: v.occupancyType,
      fteAllocation: v.fteAllocation,
      startDate: v.startDate.format('YYYY-MM-DD'),
      actingAllowance: v.actingAllowance,
      actingAllowanceCurrency: v.actingAllowanceCurrency,
      homePositionId: v.homePositionId,
      notes: v.notes,
    }
    try {
      await positionOccupancyApi.create(body)
      setAssignModal(false)
      message.success('Occupant assigned')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Assign failed')
    }
  }

  const openEnd = (row: PositionOccupancy) => {
    endForm.resetFields()
    endForm.setFieldsValue({ endDate: dayjs() })
    setEndModal(row)
  }

  const onEnd = async () => {
    if (!endModal) return
    const v = await endForm.validateFields()
    try {
      await positionOccupancyApi.end(endModal.id, {
        endDate: v.endDate.format('YYYY-MM-DD'),
        reason: v.reason,
        notes: v.notes,
      })
      setEndModal(null)
      message.success('Occupancy ended')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'End failed')
    }
  }

  const openReplacement = (row: PositionOccupancy) => {
    replacementForm.resetFields()
    replacementForm.setFieldsValue({
      action: 'OPEN_RECRUITMENT',
      lastWorkingDay: dayjs(),
      handoverOverlapDays: 0,
    })
    setReplacementModal(row)
  }

  const onReplacement = async () => {
    if (!replacementModal) return
    const v = await replacementForm.validateFields()
    const body: ReplacementRequest = {
      positionId,
      leavingEmployeeId: replacementModal.employeeId,
      leavingOccupancyId: replacementModal.id,
      reason: v.reason,
      lastWorkingDay: v.lastWorkingDay.format('YYYY-MM-DD'),
      action: v.action,
      replacementEmployeeId: v.replacementEmployeeId,
      replacementStartDate: v.replacementStartDate?.format('YYYY-MM-DD'),
      handoverOverlapDays: v.handoverOverlapDays,
      notes: v.notes,
    }
    try {
      await positionReplacementApi.create(body)
      setReplacementModal(null)
      message.success('Replacement request created (DRAFT)')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not create replacement')
    }
  }

  const submitReplacement = async (id: string) => {
    try {
      await positionReplacementApi.submit(id)
      message.success('Submitted for approval')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Submit failed')
    }
  }
  const approveReplacement = async (id: string) => {
    try {
      await positionReplacementApi.approve(id)
      message.success('Approved')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Approve failed')
    }
  }
  const completeReplacement = async (id: string) => {
    try {
      await positionReplacementApi.complete(id)
      message.success('Marked complete')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Complete failed')
    }
  }

  // ── Render ───────────────────────────────────────────────────────

  const occCols: ColumnsType<PositionOccupancy> = [
    {
      title: 'Occupant',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <strong>{employeeLabel(r.employeeId)}</strong>
          {r.actingAllowance ? (
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              Acting allowance: {r.actingAllowanceCurrency ?? 'AZN'}{' '}
              {Number(r.actingAllowance).toLocaleString()}
            </Typography.Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: 'Type',
      width: 120,
      render: (_, r) => (
        <Tag color={OCCUPANCY_TYPE_COLOR[r.occupancyType]}>
          {OCCUPANCY_TYPE_LABEL[r.occupancyType]}
        </Tag>
      ),
    },
    {
      title: 'FTE',
      dataIndex: 'fteAllocation',
      width: 70,
      align: 'right' as const,
      render: (v: number) => Number(v).toFixed(2),
    },
    {
      title: 'Window',
      width: 200,
      render: (_, r) => (
        <span>
          {r.startDate} →{' '}
          {r.endDate ?? <Typography.Text type="success">active</Typography.Text>}
        </span>
      ),
    },
    {
      title: 'End reason',
      dataIndex: 'endReason',
      render: (v: string) => v ?? '',
    },
    // M250 — Phase F.2: per-row Grants button. Shows a badge with the
    // pending count so HR can spot un-actioned items at a glance.
    {
      title: 'Grants',
      width: 130,
      render: (_, r) => {
        const pending = pendingCounts[r.id] ?? 0
        return (
          <Button
            size="small"
            type={pending > 0 ? 'primary' : 'default'}
            ghost={pending > 0}
            onClick={() => openGrants(r)}
          >
            🎁 {pending > 0 ? `${pending} pending` : 'View'}
          </Button>
        )
      },
    },
    ...(canEdit
      ? [
          {
            title: '',
            width: 220,
            render: (_: unknown, r: PositionOccupancy) =>
              r.endDate ? null : (
                <Space size={4}>
                  <Button size="small" onClick={() => openEnd(r)}>
                    End
                  </Button>
                  <Button size="small" onClick={() => openReplacement(r)}>
                    Replace
                  </Button>
                </Space>
              ),
          } as ColumnsType<PositionOccupancy>[number],
        ]
      : []),
  ]

  const replCols: ColumnsType<PositionReplacement> = [
    {
      title: 'Leaving',
      render: (_, r) => employeeLabel(r.leavingEmployeeId),
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      render: (v: string) => v.replace(/_/g, ' '),
    },
    { title: 'Last working day', dataIndex: 'lastWorkingDay', width: 130 },
    {
      title: 'Action',
      dataIndex: 'action',
      render: (a: ReplacementAction) => REPLACEMENT_ACTION_LABEL[a],
    },
    {
      title: 'Replacement',
      render: (_, r) => employeeLabel(r.replacementEmployeeId),
    },
    {
      title: 'Status',
      width: 150,
      render: (_, r) => (
        <Tag color={REPLACEMENT_STATUS_COLOR[r.status]}>
          {REPLACEMENT_STATUS_LABEL[r.status]}
        </Tag>
      ),
    },
    ...(canEdit
      ? [
          {
            title: '',
            width: 200,
            render: (_: unknown, r: PositionReplacement) => (
              <Space size={4}>
                {(r.status === 'DRAFT' || r.status === 'REJECTED') && (
                  <Button size="small" onClick={() => submitReplacement(r.id)}>
                    Submit
                  </Button>
                )}
                {r.status === 'PENDING_APPROVAL' && (
                  <Button size="small" type="primary" onClick={() => approveReplacement(r.id)}>
                    Approve
                  </Button>
                )}
                {r.status === 'APPROVED' && (
                  <Popconfirm
                    title="Mark this replacement complete?"
                    onConfirm={() => completeReplacement(r.id)}
                  >
                    <Button size="small" type="primary">
                      Complete
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          } as ColumnsType<PositionReplacement>[number],
        ]
      : []),
  ]

  const showActingAllowance =
    Form.useWatch('occupancyType', assignForm) === 'ACTING' ||
    Form.useWatch('occupancyType', assignForm) === 'TEMPORARY'
  const showHomePosition = showActingAllowance
  const showReplacementEmployee = (a?: ReplacementAction) =>
    a === 'INTERNAL_TRANSFER' || a === 'ACTING'

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        size="small"
        title={
          <Space>
            <span>Occupants</span>
            <Tag color="blue">{active.length} active</Tag>
            <Tag>FTE used: {totalActiveFte.toFixed(2)}</Tag>
          </Space>
        }
        extra={
          canEdit && (
            <Button type="primary" onClick={openAssign}>
              + Assign occupant
            </Button>
          )
        }
      >
        <Table
          size="small"
          rowKey="id"
          loading={loading}
          columns={occCols}
          dataSource={occupancies}
          pagination={false}
          locale={{ emptyText: 'No occupants yet.' }}
        />
      </Card>

      {replacements.length > 0 && (
        <Card size="small" title="Replacement workflow">
          <Table
            size="small"
            rowKey="id"
            columns={replCols}
            dataSource={replacements}
            pagination={false}
          />
        </Card>
      )}

      {/* ── Assign modal ── */}
      <Modal
        title="Assign occupant"
        open={assignModal}
        onOk={onAssign}
        onCancel={() => setAssignModal(false)}
        okText="Assign"
        destroyOnClose
        width={520}
      >
        <Form form={assignForm} layout="vertical" preserve={false}>
          <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
            <EmployeePicker placeholder="Select employee" />
          </Form.Item>
          <Form.Item name="occupancyType" label="Type" rules={[{ required: true }]}>
            <Select options={OCCUPANCY_TYPE_OPTIONS} />
          </Form.Item>
          <Space size="small">
            <Form.Item name="fteAllocation" label="FTE allocation" rules={[{ required: true }]}>
              <InputNumber min={0.01} max={1.0} step={0.25} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="startDate" label="Start date" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
          </Space>
          {showHomePosition && (
            <>
              <Descriptions size="small" column={1} bordered style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Acting / Temporary fields">
                  Set the home position the employee returns to + the acting allowance.
                </Descriptions.Item>
              </Descriptions>
              <Form.Item name="homePositionId" label="Home position (where they return)">
                <Input placeholder="Position UUID (optional)" />
              </Form.Item>
              <Space size="small">
                <Form.Item name="actingAllowance" label="Acting allowance / month">
                  <InputNumber min={0} step={50} style={{ width: 160 }} />
                </Form.Item>
                <Form.Item name="actingAllowanceCurrency" label="Currency">
                  <Select
                    style={{ width: 90 }}
                    options={['AZN', 'USD', 'EUR'].map((c) => ({ value: c, label: c }))}
                  />
                </Form.Item>
              </Space>
            </>
          )}
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── End modal ── */}
      <Modal
        title={endModal ? `End occupancy — ${employeeLabel(endModal.employeeId)}` : ''}
        open={!!endModal}
        onOk={onEnd}
        onCancel={() => setEndModal(null)}
        okText="End"
        destroyOnClose
      >
        <Form form={endForm} layout="vertical" preserve={false}>
          <Form.Item name="endDate" label="End date" rules={[{ required: true }]}>
            <DatePicker style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="reason" label="Reason">
            <Select allowClear options={END_REASON_OPTIONS} />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Replacement modal ── */}
      <Modal
        title={
          replacementModal
            ? `Replacement workflow — ${employeeLabel(replacementModal.employeeId)}`
            : ''
        }
        open={!!replacementModal}
        onOk={onReplacement}
        onCancel={() => setReplacementModal(null)}
        okText="Create"
        destroyOnClose
        width={560}
      >
        <Form form={replacementForm} layout="vertical" preserve={false}>
          <Form.Item name="reason" label="Reason" rules={[{ required: true }]}>
            <Select
              options={[
                'RESIGNATION',
                'TERMINATION',
                'RETIREMENT',
                'TRANSFER',
                'PROMOTION',
                'MATERNITY',
                'LONG_LEAVE',
                'MEDICAL_LEAVE',
              ].map((r) => ({ value: r, label: r.replace(/_/g, ' ') }))}
            />
          </Form.Item>
          <Form.Item name="lastWorkingDay" label="Last working day" rules={[{ required: true }]}>
            <DatePicker />
          </Form.Item>
          <Form.Item name="action" label="Action" rules={[{ required: true }]}>
            <Select options={REPLACEMENT_ACTION_OPTIONS} />
          </Form.Item>
          <Form.Item shouldUpdate noStyle>
            {() =>
              showReplacementEmployee(replacementForm.getFieldValue('action')) && (
                <>
                  <Form.Item
                    name="replacementEmployeeId"
                    label="Replacement employee"
                    rules={[{ required: true }]}
                  >
                    <EmployeePicker placeholder="Select employee" />
                  </Form.Item>
                  <Space size="small">
                    <Form.Item name="replacementStartDate" label="Replacement start">
                      <DatePicker />
                    </Form.Item>
                    <Form.Item name="handoverOverlapDays" label="Overlap days">
                      <InputNumber min={0} max={365} style={{ width: 110 }} />
                    </Form.Item>
                  </Space>
                </>
              )
            }
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── M250 — Grants modal ── */}
      <Modal
        title={
          grantsOpen
            ? `Profile grants for ${employeeLabel(grantsOpen.employeeId)}`
            : 'Grants'
        }
        open={!!grantsOpen}
        onCancel={() => setGrantsOpen(null)}
        footer={null}
        width={780}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 12 }}>
          Auto-created from the position's mandatory profile items when the
          occupancy was opened. Mark each PENDING item ACTIVE as you set it
          up downstream (allowance, training enrolment, document collected, etc.).
        </Typography.Paragraph>
        <Table
          size="small"
          rowKey="id"
          loading={grantsLoading}
          dataSource={grants}
          pagination={false}
          locale={{ emptyText: 'No grants — the position has no mandatory profile items.' }}
          columns={[
            {
              title: 'Type',
              width: 160,
              render: (_, r) => (
                <Tag color={PROFILE_ITEM_TYPE_COLOR[r.itemType]}>
                  {PROFILE_ITEM_TYPE_ICON[r.itemType]} {PROFILE_ITEM_TYPE_LABEL[r.itemType]}
                </Tag>
              ),
            },
            {
              title: 'Label',
              render: (_, r) => (
                <Space direction="vertical" size={0}>
                  <strong>{r.label}</strong>
                  {r.referenceCode && (
                    <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                      ref: {r.referenceCode}
                    </Typography.Text>
                  )}
                </Space>
              ),
            },
            {
              title: 'Amount',
              align: 'right' as const,
              width: 130,
              render: (_, r) =>
                r.valueAmount != null
                  ? `${r.currency ?? ''} ${Number(r.valueAmount).toLocaleString(undefined, {
                      minimumFractionDigits: 2,
                      maximumFractionDigits: 2,
                    })}`
                  : '—',
            },
            {
              title: 'Status',
              width: 180,
              render: (_, r) => (
                <Space direction="vertical" size={0}>
                  <Tag color={GRANT_STATUS_COLOR[r.status]}>
                    {GRANT_STATUS_LABEL[r.status]}
                  </Tag>
                  {/* M251 — when the grant resolved into a real downstream
                      row (e.g. employee_allowance), show the type so HR
                      knows payroll is already picking it up. */}
                  {r.downstreamEntityType && (
                    <Typography.Text type="success" style={{ fontSize: 11 }}>
                      ⚡ {r.downstreamEntityType.replace(/_/g, ' ').toLowerCase()}
                    </Typography.Text>
                  )}
                  {r.failureReason && (
                    <Typography.Text type="danger" style={{ fontSize: 11 }}>
                      {r.failureReason}
                    </Typography.Text>
                  )}
                </Space>
              ),
            },
            {
              title: '',
              width: 180,
              render: (_, r) => (
                <Space size={4}>
                  {r.status === 'PENDING' && (
                    <Button size="small" type="primary" onClick={() => markGrantActive(r.id)}>
                      Mark active
                    </Button>
                  )}
                  {(r.status === 'ACTIVE' || r.status === 'PENDING') && (
                    <Button size="small" danger onClick={() => revokeGrant(r.id)}>
                      Revoke
                    </Button>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Modal>
    </Space>
  )
}
