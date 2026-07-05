// M455 — Expense reimbursement batches (HR only).
// Create batches from approved claims, approve, mark paid.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Divider,
  Drawer,
  Input,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { api } from '../api/client'
import { expenseClaimsApi, type ClaimResponse } from '../api/expenseClaims'

const { Title, Text } = Typography

type ReimbursementBatchStatus = 'DRAFT' | 'APPROVED' | 'PAID'

const STATUS_COLOR: Record<ReimbursementBatchStatus, string> = {
  DRAFT: 'blue',
  APPROVED: 'green',
  PAID: 'purple',
}

interface BatchItemResponse {
  id: string
  expenseClaimId: string
  amount: number
}

interface ReimbursementBatchResponse {
  id: string
  batchNo: string
  status: ReimbursementBatchStatus
  totalAmount: number
  currency: string
  createdAt: string
  createdBy: string
  approvedBy?: string | null
  approvedAt?: string | null
  paidAt?: string | null
  paymentRef?: string | null
  items: BatchItemResponse[]
}

interface CreateBatchRequest {
  claimIds: string[]
}

interface PayRequest {
  payrollRunId?: string | null
  paymentRef?: string
}

const reimbursementApi = {
  list: (status?: ReimbursementBatchStatus) =>
    api.get<ReimbursementBatchResponse[]>('/business-trips/reimbursement-batches', {
      params: status ? { status } : {},
    }).then((r) => r.data),
  create: (req: CreateBatchRequest) =>
    api.post<ReimbursementBatchResponse>('/business-trips/reimbursement-batches', req).then((r) => r.data),
  approve: (id: string) =>
    api.post<ReimbursementBatchResponse>(`/business-trips/reimbursement-batches/${id}/approve`).then((r) => r.data),
  markPaid: (id: string, req?: PayRequest) =>
    api.post<ReimbursementBatchResponse>(`/business-trips/reimbursement-batches/${id}/pay`, req).then((r) => r.data),
}

export function ReimbursementBatchesPage() {
  const { message } = AntdApp.useApp()
  const [batches, setBatches] = useState<ReimbursementBatchResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [payOpen, setPayOpen] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [selected, setSelected] = useState<ReimbursementBatchResponse | null>(null)
  const [approvedClaims, setApprovedClaims] = useState<ClaimResponse[]>([])
  const [selectedClaims, setSelectedClaims] = useState<string[]>([])
  const [paymentRef, setPaymentRef] = useState('')
  const [statusFilter, setStatusFilter] = useState<ReimbursementBatchStatus | undefined>()

  const load = (status?: ReimbursementBatchStatus) => {
    setLoading(true)
    reimbursementApi.list(status)
      .then(setBatches)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load batches'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load(statusFilter) }, [statusFilter])

  const openCreate = async () => {
    try {
      const claims = await expenseClaimsApi.list('APPROVED')
      setApprovedClaims(claims)
      setSelectedClaims([])
      setCreateOpen(true)
    } catch (e) {
      message.error('Failed to load approved claims')
    }
  }

  const handleCreate = async () => {
    if (selectedClaims.length === 0) {
      message.warning('Select at least one claim')
      return
    }
    try {
      await reimbursementApi.create({ claimIds: selectedClaims })
      message.success('Batch created')
      setCreateOpen(false)
      load(statusFilter)
    } catch (e) {
      message.error((e as any)?.response?.data?.message ?? 'Create failed')
    }
  }

  const handleApprove = async (id: string) => {
    try {
      const updated = await reimbursementApi.approve(id)
      message.success('Batch approved')
      setBatches((prev) => prev.map((b) => (b.id === id ? updated : b)))
      if (selected?.id === id) setSelected(updated)
    } catch (e) {
      message.error((e as any)?.response?.data?.message ?? 'Approve failed')
    }
  }

  const openPay = (batch: ReimbursementBatchResponse) => {
    setSelected(batch)
    setPaymentRef('')
    setPayOpen(true)
  }

  const handlePay = async () => {
    if (!selected) return
    try {
      const updated = await reimbursementApi.markPaid(selected.id, { paymentRef })
      message.success('Batch marked paid')
      setBatches((prev) => prev.map((b) => (b.id === selected.id ? updated : b)))
      setPayOpen(false)
    } catch (e) {
      message.error((e as any)?.response?.data?.message ?? 'Mark paid failed')
    }
  }

  const showDetail = (b: ReimbursementBatchResponse) => {
    setSelected(b)
    setDetailOpen(true)
  }

  const columns: ColumnsType<ReimbursementBatchResponse> = [
    {
      title: 'Batch #',
      dataIndex: 'batchNo',
      width: 140,
      render: (v, r) => (
        <Button type="link" onClick={() => showDetail(r)}>
          {v}
        </Button>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (v) => <Tag color={STATUS_COLOR[v as ReimbursementBatchStatus]}>{v}</Tag>,
    },
    {
      title: 'Total',
      dataIndex: 'totalAmount',
      width: 130,
      render: (v, r) => (
        <Text strong>
          {r.currency} {v.toFixed(2)}
        </Text>
      ),
    },
    {
      title: 'Items',
      key: 'items',
      width: 80,
      render: (_, r) => r.items.length,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      width: 160,
      render: (v) => dayjs(v).format('DD MMM YYYY HH:mm'),
    },
    {
      title: 'Created By',
      dataIndex: 'createdBy',
      width: 120,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 180,
      render: (_, r) => (
        <Space size="small">
          {r.status === 'DRAFT' && (
            <Button size="small" type="primary" onClick={() => handleApprove(r.id)}>
              Approve
            </Button>
          )}
          {r.status === 'APPROVED' && (
            <Button size="small" onClick={() => openPay(r)}>
              Mark Paid
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const totals = batches.reduce(
    (acc, b) => {
      acc[b.status] = (acc[b.status] ?? 0) + 1
      acc.totalAmount = (acc.totalAmount ?? 0) + b.totalAmount
      return acc
    },
    {} as Record<string, number>,
  )

  return (
    <div>
      <Card
        title={<Title level={4}>Reimbursement Batches</Title>}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            New Batch
          </Button>
        }
      >
        <Space size="large" style={{ marginBottom: 16 }}>
          <Statistic title="Draft" value={totals.DRAFT ?? 0} valueStyle={{ color: '#1677ff' }} />
          <Statistic title="Approved" value={totals.APPROVED ?? 0} valueStyle={{ color: '#52c41a' }} />
          <Statistic title="Paid" value={totals.PAID ?? 0} valueStyle={{ color: '#722ed1' }} />
          <Statistic
            title="Total Amount"
            value={totals.totalAmount?.toFixed(2) ?? 0}
            prefix="AZN"
          />
        </Space>
        <Divider />
        <Select
          allowClear
          placeholder="Filter by status"
          style={{ width: 200, marginBottom: 16 }}
          value={statusFilter}
          onChange={setStatusFilter}
          options={[
            { value: 'DRAFT', label: 'Draft' },
            { value: 'APPROVED', label: 'Approved' },
            { value: 'PAID', label: 'Paid' },
          ]}
        />
        <Table
          rowKey="id"
          columns={columns}
          dataSource={batches}
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Modal
        title="Create Reimbursement Batch"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={handleCreate}
        width={800}
      >
        <div style={{ marginTop: 16 }}>
          <Text>Select approved expense claims to include in this batch:</Text>
          <Table
            rowKey="id"
            rowSelection={{
              selectedRowKeys: selectedClaims,
              onChange: (keys) => setSelectedClaims(keys as string[]),
            }}
            columns={[
              { title: 'Claim #', dataIndex: 'claimNo', width: 120 },
              { title: 'Employee', dataIndex: 'employeeName', width: 180 },
              { title: 'Trip #', dataIndex: 'tripNo', width: 100 },
              {
                title: 'Amount',
                dataIndex: 'totalAmount',
                width: 120,
                render: (v, r) => `${r.currency} ${v.toFixed(2)}`,
              },
            ]}
            dataSource={approvedClaims}
            pagination={false}
            scroll={{ y: 400 }}
            style={{ marginTop: 16 }}
          />
        </div>
      </Modal>

      <Modal
        title="Mark Batch Paid"
        open={payOpen}
        onCancel={() => setPayOpen(false)}
        onOk={handlePay}
      >
        <div style={{ marginTop: 16 }}>
          <Input
            placeholder="Payment reference (e.g. bank transfer ID)"
            value={paymentRef}
            onChange={(e) => setPaymentRef(e.target.value)}
          />
          <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
            Optional: enter payment reference for audit trail
          </Text>
        </div>
      </Modal>

      <Drawer
        title="Batch Details"
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={600}
      >
        {selected && (
          <>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="Batch #">{selected.batchNo}</Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={STATUS_COLOR[selected.status]}>{selected.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Total">
                <Text strong style={{ fontSize: 16 }}>
                  {selected.currency} {selected.totalAmount.toFixed(2)}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="Items">{selected.items.length}</Descriptions.Item>
              <Descriptions.Item label="Created At">
                {dayjs(selected.createdAt).format('DD MMM YYYY HH:mm')}
              </Descriptions.Item>
              <Descriptions.Item label="Created By">{selected.createdBy}</Descriptions.Item>
              {selected.approvedBy && (
                <Descriptions.Item label="Approved By">{selected.approvedBy}</Descriptions.Item>
              )}
              {selected.approvedAt && (
                <Descriptions.Item label="Approved At">
                  {dayjs(selected.approvedAt).format('DD MMM YYYY HH:mm')}
                </Descriptions.Item>
              )}
              {selected.paidAt && (
                <Descriptions.Item label="Paid At">
                  {dayjs(selected.paidAt).format('DD MMM YYYY HH:mm')}
                </Descriptions.Item>
              )}
              {selected.paymentRef && (
                <Descriptions.Item label="Payment Ref">{selected.paymentRef}</Descriptions.Item>
              )}
            </Descriptions>
            <Divider />
            <Title level={5}>Batch Items</Title>
            <Table
              rowKey="id"
              columns={[
                { title: 'Claim ID', dataIndex: 'expenseClaimId', width: 280 },
                {
                  title: 'Amount',
                  dataIndex: 'amount',
                  width: 120,
                  render: (v) => `${selected.currency} ${v.toFixed(2)}`,
                },
              ]}
              dataSource={selected.items}
              pagination={false}
              size="small"
            />
          </>
        )}
      </Drawer>
    </div>
  )
}
