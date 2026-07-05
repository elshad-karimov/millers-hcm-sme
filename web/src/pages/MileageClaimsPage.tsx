// M453 — Mileage claims (employee submit + HR/manager approve).
// Employee view: my claims + submit modal.
// HR/manager view (tabs): all/team claims with approve/reject/mark-paid actions.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Divider,
  Drawer,
  Form,
  Input,
  InputNumber,
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
import { selfApi } from '../api/self'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

type VehicleType = 'PERSONAL_CAR' | 'COMPANY_CAR' | 'MOTORBIKE'
type MileageClaimStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'PAID'

const STATUS_COLOR: Record<MileageClaimStatus, string> = {
  DRAFT: 'default',
  SUBMITTED: 'blue',
  APPROVED: 'green',
  REJECTED: 'red',
  PAID: 'purple',
}

const VEHICLE_TYPE_LABEL: Record<VehicleType, string> = {
  PERSONAL_CAR: 'Personal Car',
  COMPANY_CAR: 'Company Car',
  MOTORBIKE: 'Motorbike',
}

interface MileageClaimRequest {
  employeeId: string
  claimDate: string
  vehicleType: VehicleType
  startLocation: string
  endLocation: string
  distanceKm: number
  ratePerKm: number
  notes?: string
}

interface MileageClaimResponse {
  id: string
  claimNo: string
  employeeId: string
  claimDate: string
  vehicleType: VehicleType
  startLocation: string
  endLocation: string
  distanceKm: number
  ratePerKm: number
  totalAmount: number
  currency: string
  status: MileageClaimStatus
  approvedBy?: string | null
  approvedAt?: string | null
  rejectedAt?: string | null
  rejectionReason?: string | null
  paidAt?: string | null
  notes?: string | null
  createdAt: string
}

interface FormValues {
  claimDate: dayjs.Dayjs
  vehicleType: VehicleType
  startLocation: string
  endLocation: string
  distanceKm: number
  ratePerKm: number
  notes?: string
}

const mileageClaimApi = {
  submit: (req: MileageClaimRequest) =>
    api.post<MileageClaimResponse>('/business-trips/mileage-claims', req).then((r) => r.data),
  approve: (id: string) =>
    api.post<MileageClaimResponse>(`/business-trips/mileage-claims/${id}/approve`).then((r) => r.data),
  reject: (id: string, reason?: string) =>
    api.post<MileageClaimResponse>(`/business-trips/mileage-claims/${id}/reject`, { reason }).then((r) => r.data),
  markPaid: (id: string) =>
    api.post<MileageClaimResponse>(`/business-trips/mileage-claims/${id}/pay`).then((r) => r.data),
  list: (status?: MileageClaimStatus) =>
    api.get<MileageClaimResponse[]>('/business-trips/mileage-claims', { params: status ? { status } : {} }).then((r) => r.data),
  forEmployee: (employeeId: string) =>
    api.get<MileageClaimResponse[]>(`/business-trips/mileage-claims/employees/${employeeId}`).then((r) => r.data),
}

export function MileageClaimsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const [form] = Form.useForm<FormValues>()
  const isHR = hasRole(...RoleSets.HR_WRITE)
  const [employeeId, setEmployeeId] = useState<string | null>(null)
  const [claims, setClaims] = useState<MileageClaimResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [submitOpen, setSubmitOpen] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [selected, setSelected] = useState<MileageClaimResponse | null>(null)
  const [rejectOpen, setRejectOpen] = useState(false)
  const [rejectReason, setRejectReason] = useState('')
  const [statusFilter, setStatusFilter] = useState<MileageClaimStatus | undefined>()

  // Live total preview
  const distanceKm = Form.useWatch('distanceKm', form)
  const ratePerKm = Form.useWatch('ratePerKm', form)
  const liveTotal = (distanceKm || 0) * (ratePerKm || 0)

  useEffect(() => {
    selfApi.profile().then((p) => setEmployeeId(p.id))
  }, [])

  const loadMyClaims = () => {
    if (!employeeId) return
    setLoading(true)
    mileageClaimApi.forEmployee(employeeId)
      .then(setClaims)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load claims'))
      .finally(() => setLoading(false))
  }

  const loadAllClaims = (status?: MileageClaimStatus) => {
    setLoading(true)
    mileageClaimApi.list(status)
      .then(setClaims)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load claims'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (!isHR && employeeId) {
      loadMyClaims()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId, isHR])

  useEffect(() => {
    if (isHR) {
      loadAllClaims(statusFilter)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isHR, statusFilter])

  const openSubmit = () => {
    form.resetFields()
    form.setFieldsValue({ ratePerKm: 0.30, claimDate: dayjs() })
    setSubmitOpen(true)
  }

  const onSubmit = async (v: FormValues) => {
    if (!employeeId) return
    const payload: MileageClaimRequest = {
      employeeId,
      claimDate: v.claimDate.format('YYYY-MM-DD'),
      vehicleType: v.vehicleType,
      startLocation: v.startLocation,
      endLocation: v.endLocation,
      distanceKm: v.distanceKm,
      ratePerKm: v.ratePerKm,
      notes: v.notes,
    }
    try {
      await mileageClaimApi.submit(payload)
      message.success('Claim submitted')
      setSubmitOpen(false)
      if (!isHR) loadMyClaims()
      else loadAllClaims(statusFilter)
    } catch (e) {
      message.error((e as any)?.response?.data?.message ?? 'Submit failed')
    }
  }

  const handleApprove = async (id: string) => {
    try {
      const updated = await mileageClaimApi.approve(id)
      message.success('Claim approved')
      setClaims((prev) => prev.map((c) => (c.id === id ? updated : c)))
      if (selected?.id === id) setSelected(updated)
    } catch (e) {
      message.error((e as any)?.response?.data?.message ?? 'Approve failed')
    }
  }

  const openReject = (claim: MileageClaimResponse) => {
    setSelected(claim)
    setRejectReason('')
    setRejectOpen(true)
  }

  const handleReject = async () => {
    if (!selected) return
    try {
      const updated = await mileageClaimApi.reject(selected.id, rejectReason)
      message.success('Claim rejected')
      setClaims((prev) => prev.map((c) => (c.id === selected.id ? updated : c)))
      setRejectOpen(false)
    } catch (e) {
      message.error((e as any)?.response?.data?.message ?? 'Reject failed')
    }
  }

  const handleMarkPaid = async (id: string) => {
    try {
      const updated = await mileageClaimApi.markPaid(id)
      message.success('Claim marked paid')
      setClaims((prev) => prev.map((c) => (c.id === id ? updated : c)))
      if (selected?.id === id) setSelected(updated)
    } catch (e) {
      message.error((e as any)?.response?.data?.message ?? 'Mark paid failed')
    }
  }

  const showDetail = (c: MileageClaimResponse) => {
    setSelected(c)
    setDetailOpen(true)
  }

  const columns: ColumnsType<MileageClaimResponse> = [
    {
      title: 'Claim #',
      dataIndex: 'claimNo',
      width: 130,
      render: (v, r) => (
        <Button type="link" onClick={() => showDetail(r)}>
          {v}
        </Button>
      ),
    },
    { title: 'Date', dataIndex: 'claimDate', width: 110 },
    {
      title: 'Vehicle',
      dataIndex: 'vehicleType',
      width: 130,
      render: (v) => VEHICLE_TYPE_LABEL[v as VehicleType],
    },
    { title: 'From', dataIndex: 'startLocation', width: 140 },
    { title: 'To', dataIndex: 'endLocation', width: 140 },
    { title: 'Distance (km)', dataIndex: 'distanceKm', width: 110 },
    {
      title: 'Total',
      dataIndex: 'totalAmount',
      width: 110,
      render: (v, r) => `${r.currency} ${v.toFixed(2)}`,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (v) => <Tag color={STATUS_COLOR[v as MileageClaimStatus]}>{v}</Tag>,
    },
    ...(isHR
      ? [
          {
            title: 'Actions',
            key: 'actions',
            width: 220,
            render: (_: any, r: MileageClaimResponse) => (
              <Space size="small">
                {r.status === 'SUBMITTED' && (
                  <>
                    <Button size="small" type="primary" onClick={() => handleApprove(r.id)}>
                      Approve
                    </Button>
                    <Button size="small" danger onClick={() => openReject(r)}>
                      Reject
                    </Button>
                  </>
                )}
                {r.status === 'APPROVED' && (
                  <Button size="small" onClick={() => handleMarkPaid(r.id)}>
                    Mark Paid
                  </Button>
                )}
              </Space>
            ),
          },
        ]
      : []),
  ]

  const totals = claims.reduce(
    (acc, c) => {
      acc[c.status] = (acc[c.status] ?? 0) + 1
      return acc
    },
    {} as Record<string, number>,
  )

  return (
    <div>
      <Card
        title={<Title level={4}>Mileage Claims</Title>}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openSubmit}>
            New Claim
          </Button>
        }
      >
        {isHR && (
          <>
            <Space size="large" style={{ marginBottom: 16 }}>
              <Statistic title="Submitted" value={totals.SUBMITTED ?? 0} valueStyle={{ color: '#1677ff' }} />
              <Statistic title="Approved" value={totals.APPROVED ?? 0} valueStyle={{ color: '#52c41a' }} />
              <Statistic title="Paid" value={totals.PAID ?? 0} valueStyle={{ color: '#722ed1' }} />
            </Space>
            <Divider />
            <Select
              allowClear
              placeholder="Filter by status"
              style={{ width: 200, marginBottom: 16 }}
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: 'SUBMITTED', label: 'Submitted' },
                { value: 'APPROVED', label: 'Approved' },
                { value: 'REJECTED', label: 'Rejected' },
                { value: 'PAID', label: 'Paid' },
              ]}
            />
          </>
        )}
        <Table
          rowKey="id"
          columns={columns}
          dataSource={claims}
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Modal
        title="Submit Mileage Claim"
        open={submitOpen}
        onCancel={() => setSubmitOpen(false)}
        onOk={() => form.submit()}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={onSubmit} style={{ marginTop: 16 }}>
          <Form.Item
            name="claimDate"
            label="Date"
            rules={[{ required: true, message: 'Required' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="vehicleType"
            label="Vehicle Type"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select
              options={[
                { value: 'PERSONAL_CAR', label: 'Personal Car' },
                { value: 'COMPANY_CAR', label: 'Company Car' },
                { value: 'MOTORBIKE', label: 'Motorbike' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="startLocation"
            label="From"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="e.g. Baku Office" />
          </Form.Item>
          <Form.Item
            name="endLocation"
            label="To"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="e.g. Sumqayit Plant" />
          </Form.Item>
          <Form.Item
            name="distanceKm"
            label="Distance (km)"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} step={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="ratePerKm"
            label="Rate per km (AZN)"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
          </Form.Item>
          <Card size="small" style={{ marginBottom: 16, background: '#f0f5ff' }}>
            <Statistic
              title="Total Reimbursement"
              value={liveTotal.toFixed(2)}
              prefix="AZN"
              valueStyle={{ color: '#1677ff' }}
            />
          </Card>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="Claim Details"
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={500}
      >
        {selected && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="Claim #">{selected.claimNo}</Descriptions.Item>
            <Descriptions.Item label="Date">{selected.claimDate}</Descriptions.Item>
            <Descriptions.Item label="Vehicle">
              {VEHICLE_TYPE_LABEL[selected.vehicleType]}
            </Descriptions.Item>
            <Descriptions.Item label="From">{selected.startLocation}</Descriptions.Item>
            <Descriptions.Item label="To">{selected.endLocation}</Descriptions.Item>
            <Descriptions.Item label="Distance">{selected.distanceKm} km</Descriptions.Item>
            <Descriptions.Item label="Rate">
              {selected.currency} {selected.ratePerKm} / km
            </Descriptions.Item>
            <Descriptions.Item label="Total">
              <Text strong style={{ fontSize: 16 }}>
                {selected.currency} {selected.totalAmount.toFixed(2)}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="Status">
              <Tag color={STATUS_COLOR[selected.status]}>{selected.status}</Tag>
            </Descriptions.Item>
            {selected.approvedBy && (
              <Descriptions.Item label="Approved By">{selected.approvedBy}</Descriptions.Item>
            )}
            {selected.approvedAt && (
              <Descriptions.Item label="Approved At">
                {dayjs(selected.approvedAt).format('DD MMM YYYY HH:mm')}
              </Descriptions.Item>
            )}
            {selected.rejectedAt && (
              <Descriptions.Item label="Rejected At">
                {dayjs(selected.rejectedAt).format('DD MMM YYYY HH:mm')}
              </Descriptions.Item>
            )}
            {selected.rejectionReason && (
              <Descriptions.Item label="Rejection Reason">
                <Text type="danger">{selected.rejectionReason}</Text>
              </Descriptions.Item>
            )}
            {selected.paidAt && (
              <Descriptions.Item label="Paid At">
                {dayjs(selected.paidAt).format('DD MMM YYYY HH:mm')}
              </Descriptions.Item>
            )}
            {selected.notes && (
              <Descriptions.Item label="Notes">{selected.notes}</Descriptions.Item>
            )}
          </Descriptions>
        )}
      </Drawer>

      <Modal
        title="Reject Claim"
        open={rejectOpen}
        onCancel={() => setRejectOpen(false)}
        onOk={handleReject}
      >
        <Input.TextArea
          rows={3}
          placeholder="Reason for rejection..."
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
        />
      </Modal>
    </div>
  )
}
