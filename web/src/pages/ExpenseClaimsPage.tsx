// M104 — Expense claims management (HR view).
// Employees submit claims; HR approves / rejects / marks paid.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { Link } from 'react-router-dom'
import {
  expenseClaimsApi,
  CLAIM_STATUS_COLOR,
  type ClaimResponse,
  type ClaimStatus,
} from '../api/expenseClaims'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const CATEGORY_LABEL: Record<string, string> = {
  ACCOMMODATION: 'Accommodation',
  MEALS: 'Meals',
  TRANSPORT: 'Transport',
  FLIGHT: 'Flight',
  VISA_FEES: 'Visa fees',
  COMMUNICATION: 'Communication',
  REGISTRATION: 'Registration',
  OTHER: 'Other',
}

export function ExpenseClaimsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canApprove = hasRole(...RoleSets.HR_WRITE)
  const canMarkPaid = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [claims, setClaims] = useState<ClaimResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState<ClaimStatus | undefined>()
  const [selected, setSelected] = useState<ClaimResponse | null>(null)
  const [rejectOpen, setRejectOpen] = useState(false)
  const [rejectReason, setRejectReason] = useState('')

  const load = (status?: ClaimStatus) => {
    setLoading(true)
    expenseClaimsApi
      .list(status)
      .then(setClaims)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load(statusFilter) }, [statusFilter])

  const act = async (action: () => Promise<ClaimResponse>, successMsg: string) => {
    try {
      const updated = await action()
      message.success(successMsg)
      setClaims((prev) => prev.map((c) => (c.id === updated.id ? updated : c)))
      if (selected?.id === updated.id) setSelected(updated)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Action failed',
      )
    }
  }

  const totals = claims.reduce(
    (acc, c) => {
      acc[c.status] = (acc[c.status] ?? 0) + 1
      return acc
    },
    {} as Record<string, number>,
  )

  const cols: ColumnsType<ClaimResponse> = [
    {
      title: 'Claim #',
      dataIndex: 'claimNo',
      width: 130,
      render: (v: string, r) => (
        <a onClick={() => setSelected(r)}>{v}</a>
      ),
    },
    { title: 'Employee', dataIndex: 'employeeName', render: (v) => v ?? '—' },
    { title: 'Destination', dataIndex: 'destination', render: (v) => v ?? '—' },
    {
      title: 'Total',
      dataIndex: 'totalAmount',
      align: 'right',
      width: 130,
      sorter: (a, b) => a.totalAmount - b.totalAmount,
      render: (v: number, r) =>
        `${r.currency} ${v.toLocaleString('en-US', { minimumFractionDigits: 2 })}`,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: ClaimStatus) => <Tag color={CLAIM_STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Submitted',
      dataIndex: 'submittedAt',
      width: 130,
      render: (v?: string | null) => v ? dayjs(v).format('YYYY-MM-DD') : '—',
    },
    {
      title: '',
      width: 200,
      render: (_, r) => (
        <Space>
          {r.status === 'SUBMITTED' && canApprove && (
            <>
              <Popconfirm title="Approve?" onConfirm={() =>
                act(() => expenseClaimsApi.approve(r.id), 'Approved')
              } okText="Yes">
                <Button size="small" type="primary">Approve</Button>
              </Popconfirm>
              <Button size="small" danger onClick={() => { setSelected(r); setRejectOpen(true) }}>
                Reject
              </Button>
            </>
          )}
          {r.status === 'APPROVED' && canMarkPaid && (
            <Popconfirm title="Mark as paid?" onConfirm={() =>
              act(() => expenseClaimsApi.markPaid(r.id), 'Marked paid')
            } okText="Yes">
              <Button size="small">Mark paid</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Expense claims</Title>

      <Row gutter={[12, 12]}>
        {(['SUBMITTED', 'APPROVED', 'PAID', 'REJECTED'] as ClaimStatus[]).map((s) => (
          <Col xs={12} md={6} key={s}>
            <Card size="small" hoverable onClick={() => setStatusFilter(s)}>
              <Statistic
                title={s}
                value={totals[s] ?? 0}
                valueStyle={{ color: CLAIM_STATUS_COLOR[s] === 'default' ? undefined : CLAIM_STATUS_COLOR[s] }}
              />
            </Card>
          </Col>
        ))}
      </Row>

      <Card
        title="All claims"
        extra={
          <Space>
            <Select
              allowClear
              placeholder="All statuses"
              style={{ width: 160 }}
              value={statusFilter}
              onChange={(v) => setStatusFilter(v)}
              options={(['DRAFT','SUBMITTED','APPROVED','REJECTED','PAID'] as ClaimStatus[])
                .map((s) => ({ value: s, label: s }))}
            />
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={cols}
          dataSource={claims}
          pagination={{ pageSize: 25 }}
          size="small"
          locale={{ emptyText: <Empty description="No claims found" /> }}
        />
      </Card>

      {/* Claim detail drawer */}
      <Drawer
        open={!!selected}
        title={selected ? `${selected.claimNo} — ${selected.destination ?? 'Trip'}` : ''}
        onClose={() => setSelected(null)}
        width={560}
      >
        {selected && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="Claim #">{selected.claimNo}</Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={CLAIM_STATUS_COLOR[selected.status]}>{selected.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Employee">
                <Link to={`/employees/${selected.employeeId}`}>{selected.employeeName ?? '—'}</Link>
              </Descriptions.Item>
              <Descriptions.Item label="Currency">{selected.currency}</Descriptions.Item>
              <Descriptions.Item label="Total" span={2}>
                <Text strong>
                  {selected.currency} {selected.totalAmount.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                </Text>
              </Descriptions.Item>
              {selected.notes && (
                <Descriptions.Item label="Notes" span={2}>{selected.notes}</Descriptions.Item>
              )}
              {selected.rejectionReason && (
                <Descriptions.Item label="Rejection reason" span={2}>
                  <Text type="danger">{selected.rejectionReason}</Text>
                </Descriptions.Item>
              )}
            </Descriptions>

            <Card title={`Items (${selected.items.length})`} size="small">
              <Table
                rowKey="id"
                dataSource={selected.items}
                pagination={false}
                size="small"
                columns={[
                  {
                    title: 'Category',
                    dataIndex: 'category',
                    render: (c: string) => CATEGORY_LABEL[c] ?? c,
                  },
                  { title: 'Description', dataIndex: 'description', render: (v) => v ?? '—' },
                  {
                    title: 'Amount',
                    dataIndex: 'amount',
                    align: 'right',
                    render: (v: number) =>
                      v.toLocaleString('en-US', { minimumFractionDigits: 2 }),
                  },
                  {
                    title: 'Date',
                    dataIndex: 'itemDate',
                    render: (v?: string | null) => v ?? '—',
                  },
                ]}
              />
            </Card>
          </Space>
        )}
      </Drawer>

      {/* Reject reason modal */}
      <Modal
        open={rejectOpen}
        title="Reject claim"
        onCancel={() => setRejectOpen(false)}
        onOk={async () => {
          if (!selected) return
          await act(
            () => expenseClaimsApi.reject(selected.id, rejectReason),
            'Rejected',
          )
          setRejectOpen(false)
          setRejectReason('')
        }}
        okText="Reject"
        okButtonProps={{ danger: true }}
      >
        <Input.TextArea
          rows={3}
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          placeholder="Reason for rejection (visible to the employee)"
        />
      </Modal>
    </Space>
  )
}
