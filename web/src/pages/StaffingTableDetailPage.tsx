// M245 — Staffing table detail page.
//
// Header (status pill, lifecycle buttons, export buttons) + editable
// lines table. Lines come from the same store so the totals row
// always matches what's on screen.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import {
  STAFFING_TABLE_STATUS_COLOR,
  STAFFING_TABLE_STATUS_LABEL,
  staffingTableApi,
  type StaffingTable,
  type StaffingTableLine,
  type StaffingTableLineRequest,
} from '../api/staffingTable'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

export function StaffingTableDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { message, modal } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const navigate = useNavigate()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [table, setTable] = useState<StaffingTable | null>(null)
  const [lines, setLines] = useState<StaffingTableLine[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [lineModal, setLineModal] = useState<StaffingTableLine | 'new' | null>(null)
  const [lineForm] = Form.useForm<StaffingTableLineRequest>()

  const editable = table?.status === 'DRAFT'
  const canSubmit = editable && lines.length > 0

  const refresh = () => {
    if (!id) return
    setLoading(true)
    staffingTableApi.get(id).then(setTable).catch((err) =>
      message.error(err?.response?.data?.message ?? 'Could not load'),
    )
    staffingTableApi.lines(id).then(setLines).catch(() => setLines([]))
      .finally(() => setLoading(false))
  }
  useEffect(refresh, [id])

  // ── Lifecycle actions ────────────────────────────────────────────

  const lifecycleAction = async (
    action: 'submit' | 'approve' | 'archive',
    success: string,
  ) => {
    if (!id) return
    setSubmitting(true)
    try {
      const updated = await staffingTableApi[action](id)
      setTable(updated)
      message.success(success)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Action failed')
    } finally {
      setSubmitting(false)
    }
  }

  const onReject = () => {
    let reason = ''
    modal.confirm({
      title: 'Reject this staffing table?',
      content: (
        <Input.TextArea
          rows={3}
          placeholder="Reason — required"
          onChange={(e) => (reason = e.target.value)}
        />
      ),
      okText: 'Reject',
      okButtonProps: { danger: true },
      onOk: async () => {
        if (!id || !reason.trim()) {
          message.error('Reason is required')
          throw new Error('reason')
        }
        try {
          const updated = await staffingTableApi.reject(id, reason.trim())
          setTable(updated)
          message.success('Rejected; sent back to draft state')
        } catch (err: unknown) {
          const e = err as { response?: { data?: { message?: string } } }
          message.error(e?.response?.data?.message ?? 'Failed')
          throw err
        }
      },
    })
  }

  const onGenerateFromPositions = () => {
    if (!id) return
    modal.confirm({
      title: 'Generate lines from live positions?',
      content:
        'Pulls every ACTIVE position into a snapshot line. Adds to existing lines — does not replace them.',
      okText: 'Generate',
      onOk: async () => {
        try {
          const created = await staffingTableApi.generateFromPositions(id)
          message.success(`Generated ${created.length} line(s) from live positions`)
          refresh()
        } catch (err: unknown) {
          const e = err as { response?: { data?: { message?: string } } }
          message.error(e?.response?.data?.message ?? 'Generate failed')
        }
      },
    })
  }

  const onExport = async () => {
    if (!table) return
    try {
      await staffingTableApi.exportXlsx(
        table.id,
        `stat-cedveli-${table.versionCode}.xlsx`,
      )
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Export failed')
    }
  }

  // ── Line CRUD ────────────────────────────────────────────────────

  const openLine = (line: StaffingTableLine | 'new') => {
    if (line === 'new') {
      lineForm.resetFields()
      lineForm.setFieldsValue({
        approvedHeadcount: 1,
        monthlySalary: 0,
        currency: 'AZN',
      })
    } else {
      lineForm.setFieldsValue({
        lineNo: line.lineNo,
        orgUnitLabel: line.orgUnitLabel ?? undefined,
        positionCode: line.positionCode ?? undefined,
        positionTitle: line.positionTitle,
        grade: line.grade ?? undefined,
        approvedHeadcount: line.approvedHeadcount,
        monthlySalary: line.monthlySalary,
        monthlySalaryFund: line.monthlySalaryFund,
        currency: line.currency,
        notes: line.notes ?? undefined,
      })
    }
    setLineModal(line)
  }

  const onLineOk = async () => {
    if (!id) return
    const v = await lineForm.validateFields()
    try {
      if (lineModal === 'new') {
        await staffingTableApi.addLine(id, v)
        message.success('Line added')
      } else if (lineModal) {
        await staffingTableApi.updateLine(lineModal.id, v)
        message.success('Line updated')
      }
      setLineModal(null)
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not save')
    }
  }

  const onLineDelete = async (line: StaffingTableLine) => {
    try {
      await staffingTableApi.removeLine(line.id)
      message.success('Line removed')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Delete failed')
    }
  }

  const totals = useMemo(() => {
    return lines.reduce(
      (acc, l) => ({
        hc: acc.hc + l.approvedHeadcount,
        fund: acc.fund + Number(l.monthlySalaryFund),
      }),
      { hc: 0, fund: 0 },
    )
  }, [lines])

  const fmt = (n: number, cur = 'AZN') =>
    `${cur} ${n.toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`

  const lineCols: ColumnsType<StaffingTableLine> = [
    { title: '№', dataIndex: 'lineNo', width: 60 },
    {
      title: 'Org unit',
      dataIndex: 'orgUnitLabel',
      render: (v: string) => v ?? <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'Position',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <strong>{r.positionTitle}</strong>
          {r.positionCode && (
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {r.positionCode}
            </Typography.Text>
          )}
        </Space>
      ),
    },
    { title: 'Grade', dataIndex: 'grade', width: 80 },
    {
      title: 'Count',
      dataIndex: 'approvedHeadcount',
      align: 'right' as const,
      width: 80,
    },
    {
      title: 'Salary',
      align: 'right' as const,
      width: 130,
      render: (_, r) => fmt(Number(r.monthlySalary), r.currency),
    },
    {
      title: 'Monthly fund',
      align: 'right' as const,
      width: 150,
      render: (_, r) => <strong>{fmt(Number(r.monthlySalaryFund), r.currency)}</strong>,
    },
    {
      title: 'Notes',
      dataIndex: 'notes',
      render: (v: string) => v ?? '',
    },
    ...(editable && canWrite
      ? [
          {
            title: '',
            width: 130,
            render: (_: unknown, r: StaffingTableLine) => (
              <Space size={4}>
                <Button size="small" onClick={() => openLine(r)}>
                  Edit
                </Button>
                <Popconfirm title="Delete this line?" onConfirm={() => onLineDelete(r)}>
                  <Button size="small" danger>
                    Delete
                  </Button>
                </Popconfirm>
              </Space>
            ),
          } as ColumnsType<StaffingTableLine>[number],
        ]
      : []),
  ]

  if (loading || !table) return <Card loading />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {/* ── Header card ── */}
      <Card
        title={
          <Space wrap>
            <Button size="small" onClick={() => navigate('/staffing-tables')}>
              ← Back
            </Button>
            <span>{table.title ?? table.versionCode}</span>
            <Tag color={STAFFING_TABLE_STATUS_COLOR[table.status]}>
              {STAFFING_TABLE_STATUS_LABEL[table.status]}
            </Tag>
          </Space>
        }
        extra={
          <Space wrap>
            <Button onClick={onExport}>📥 Excel</Button>
            {canWrite && editable && (
              <Button onClick={onGenerateFromPositions}>↻ Generate from positions</Button>
            )}
            {canWrite && canSubmit && (
              <Button
                type="primary"
                loading={submitting}
                onClick={() => lifecycleAction('submit', 'Submitted for approval')}
              >
                Submit for approval
              </Button>
            )}
            {canWrite && table.status === 'PENDING_APPROVAL' && (
              <>
                <Button
                  type="primary"
                  loading={submitting}
                  onClick={() => lifecycleAction('approve', 'Approved + activated')}
                >
                  Approve
                </Button>
                <Button danger loading={submitting} onClick={onReject}>
                  Reject
                </Button>
              </>
            )}
            {canWrite && (table.status === 'ACTIVE' || table.status === 'REJECTED') && (
              <Popconfirm
                title="Archive this version?"
                onConfirm={() => lifecycleAction('archive', 'Archived')}
              >
                <Button danger loading={submitting}>
                  Archive
                </Button>
              </Popconfirm>
            )}
          </Space>
        }
      >
        <Descriptions size="small" column={3}>
          <Descriptions.Item label="Version">{table.versionCode}</Descriptions.Item>
          <Descriptions.Item label="Period">
            {table.effectiveFrom} → {table.effectiveTo ?? 'open'}
          </Descriptions.Item>
          <Descriptions.Item label="Lines">{lines.length}</Descriptions.Item>
          <Descriptions.Item label="Total headcount">{totals.hc}</Descriptions.Item>
          <Descriptions.Item label="Total monthly fund" span={2}>
            <strong>{fmt(totals.fund)}</strong>
          </Descriptions.Item>
          {table.submittedAt && (
            <Descriptions.Item label="Submitted">
              {table.submittedBy} · {new Date(table.submittedAt).toLocaleString()}
            </Descriptions.Item>
          )}
          {table.approvedAt && (
            <Descriptions.Item label="Approved">
              {table.approvedBy} · {new Date(table.approvedAt).toLocaleString()}
            </Descriptions.Item>
          )}
          {table.rejectedAt && (
            <Descriptions.Item label="Rejected" span={3}>
              {table.rejectedBy} · {new Date(table.rejectedAt).toLocaleString()} ·{' '}
              <em>{table.rejectReason}</em>
            </Descriptions.Item>
          )}
          {table.notes && (
            <Descriptions.Item label="Notes" span={3}>
              {table.notes}
            </Descriptions.Item>
          )}
        </Descriptions>
      </Card>

      {/* ── Lines card ── */}
      <Card
        title="Lines"
        extra={
          editable && canWrite ? (
            <Button type="primary" onClick={() => openLine('new')}>
              + Add line
            </Button>
          ) : null
        }
      >
        <Table
          size="small"
          rowKey="id"
          columns={lineCols}
          dataSource={lines}
          pagination={false}
          locale={{ emptyText: editable ? 'No lines yet. Add manually or generate from positions.' : 'No lines.' }}
        />
      </Card>

      {/* ── Line modal ── */}
      <Modal
        title={lineModal === 'new' ? 'Add line' : 'Edit line'}
        open={!!lineModal}
        onOk={onLineOk}
        onCancel={() => setLineModal(null)}
        okText="Save"
        destroyOnClose
        width={560}
      >
        <Form form={lineForm} layout="vertical" preserve={false}>
          <Form.Item name="lineNo" label="Line #" tooltip="Leave blank to append">
            <InputNumber min={0} />
          </Form.Item>
          <Form.Item name="orgUnitLabel" label="Org unit (label)">
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="positionCode" label="Position code">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="positionTitle"
            label="Position title"
            rules={[{ required: true, max: 200 }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="grade" label="Grade">
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item
            name="approvedHeadcount"
            label="Approved count (ştat vahidi sayı)"
            rules={[{ required: true }]}
          >
            <InputNumber min={0} step={1} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="monthlySalary" label="Monthly salary">
            <InputNumber min={0} step={50} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item
            name="monthlySalaryFund"
            label="Monthly fund override (optional)"
            tooltip="Leave blank to auto-compute as count × salary."
          >
            <InputNumber min={0} step={50} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="currency" label="Currency">
            <Input maxLength={3} style={{ width: 90 }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
