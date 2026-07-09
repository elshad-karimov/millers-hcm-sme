// M458 — Asset damage/loss case queue (HR/Finance-only amounts).

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../api/client'
import { employeesApi, type Employee } from '../api/employees'

type CaseType = 'DAMAGE' | 'LOSS'
type CaseStatus = 'REPORTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'CLOSED'

interface DamageLossCase {
  id: string
  caseNo: string
  assetId: string
  assetName: string
  employeeId: string
  employeeNo: string
  employeeName: string
  caseType: CaseType
  description: string
  estimatedAmount?: number
  status: CaseStatus
  deductionProposed?: boolean
  deductionAmount?: number
  rejectionReason?: string
  closeNotes?: string
  reportedAt: string
  reviewedAt?: string
}

interface AssetOption {
  id: string
  assetName: string
  assetIdentifier?: string
}

const CASE_STATUS_COLOR: Record<CaseStatus, string> = {
  REPORTED: 'orange',
  UNDER_REVIEW: 'blue',
  APPROVED: 'green',
  REJECTED: 'red',
  CLOSED: 'default',
}

const CASE_TYPE_COLOR: Record<CaseType, string> = {
  DAMAGE: 'orange',
  LOSS: 'red',
}

export function DamageLossCasesPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<DamageLossCase[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [assets, setAssets] = useState<AssetOption[]>([])
  const [loading, setLoading] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const [actionOpen, setActionOpen] = useState(false)
  const [actionType, setActionType] = useState<'approve' | 'reject' | 'close'>('approve')
  const [current, setCurrent] = useState<DamageLossCase | null>(null)
  const [reportForm] = Form.useForm()
  const [actionForm] = Form.useForm()
  const [filterStatus, setFilterStatus] = useState<CaseStatus | undefined>()

  const load = () => {
    setLoading(true)
    api
      .get<DamageLossCase[]>('/assets/damage-loss-cases', {
        params: filterStatus ? {} : undefined,
      })
      .then((r) => setRows(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load cases'))
      .finally(() => setLoading(false))
  }

  const loadOpen = () => {
    api
      .get<DamageLossCase[]>('/assets/damage-loss-cases/open')
      .then((r) => setRows(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load cases'))
  }

  useEffect(() => {
    if (filterStatus === 'REPORTED' || filterStatus === 'UNDER_REVIEW') {
      loadOpen()
    } else {
      load()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterStatus])

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content)).catch(() => {})
    // Load active assets for report modal
    api.get<AssetOption[]>('/assets/search', { params: { status: 'ASSIGNED' } })
      .then((r) => setAssets(r.data))
      .catch(() => {})
  }, [])

  const openReport = () => {
    reportForm.resetFields()
    setReportOpen(true)
  }

  const submitReport = async () => {
    try {
      const values = await reportForm.validateFields()
      await api.post('/assets/damage-loss-cases', values)
      message.success('Case reported')
      setReportOpen(false)
      load()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Report failed')
    }
  }

  const openAction = (
    record: DamageLossCase,
    action: 'approve' | 'reject' | 'close',
  ) => {
    setCurrent(record)
    setActionType(action)
    actionForm.resetFields()
    if (action === 'approve') {
      actionForm.setFieldsValue({ deductionProposed: false })
    }
    setActionOpen(true)
  }

  const submitAction = async () => {
    if (!current) return
    try {
      const values = await actionForm.validateFields()
      const endpoint = `/assets/damage-loss-cases/${current.id}/${actionType}`
      await api.post(endpoint, values)
      message.success(`Case ${actionType}d`)
      setActionOpen(false)
      load()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? `${actionType} failed`)
    }
  }

  const columns: ColumnsType<DamageLossCase> = [
    { title: 'Case No', dataIndex: 'caseNo', width: 120 },
    {
      title: 'Asset',
      dataIndex: 'assetName',
      width: 180,
      ellipsis: true,
    },
    {
      title: 'Employee',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{r.employeeName}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {r.employeeNo}
          </Typography.Text>
        </Space>
      ),
      width: 180,
    },
    {
      title: 'Type',
      dataIndex: 'caseType',
      width: 100,
      render: (t: CaseType) => <Tag color={CASE_TYPE_COLOR[t]}>{t}</Tag>,
    },
    {
      title: 'Description',
      dataIndex: 'description',
      ellipsis: true,
    },
    {
      title: 'Estimated',
      dataIndex: 'estimatedAmount',
      width: 110,
      align: 'right',
      render: (v?: number) => (v ? `${v.toFixed(2)} AZN` : '—'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 130,
      render: (s: CaseStatus) => <Tag color={CASE_STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Deduction',
      width: 120,
      render: (_, r) => {
        if (!r.deductionProposed) return '—'
        return (
          <Typography.Text type="danger">
            {r.deductionAmount ? `${r.deductionAmount.toFixed(2)} AZN` : 'Yes'}
          </Typography.Text>
        )
      },
    },
    {
      title: 'Actions',
      width: 200,
      render: (_, r) => {
        if (r.status === 'REPORTED' || r.status === 'UNDER_REVIEW') {
          return (
            <Space size="small">
              <a onClick={() => openAction(r, 'approve')}>Approve</a>
              <a onClick={() => openAction(r, 'reject')}>Reject</a>
              <a onClick={() => openAction(r, 'close')}>Close</a>
            </Space>
          )
        }
        return null
      },
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Asset Damage & Loss Cases</Typography.Title>}
      extra={
        <Button type="primary" onClick={openReport}>
          Report Case
        </Button>
      }
    >
      <Space style={{ marginBottom: 12 }}>
        <Select
          allowClear
          placeholder="Filter by status"
          style={{ width: 200 }}
          value={filterStatus}
          onChange={setFilterStatus}
          options={[
            { value: 'REPORTED', label: 'REPORTED' },
            { value: 'UNDER_REVIEW', label: 'UNDER REVIEW' },
            { value: 'APPROVED', label: 'APPROVED' },
            { value: 'REJECTED', label: 'REJECTED' },
            { value: 'CLOSED', label: 'CLOSED' },
          ]}
        />
      </Space>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={{ pageSize: 20 }}
      />

      <Modal
        open={reportOpen}
        title="Report Damage/Loss Case"
        onCancel={() => setReportOpen(false)}
        onOk={submitReport}
        okText="Report"
        width={600}
      >
        <Form form={reportForm} layout="vertical">
          <Form.Item name="assetId" label="Asset" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={assets.map((a) => ({
                value: a.id,
                label: `${a.assetName}${a.assetIdentifier ? ` (${a.assetIdentifier})` : ''}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} - ${e.firstName} ${e.lastName}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="caseType" label="Type" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="DAMAGE">DAMAGE</Select.Option>
              <Select.Option value="LOSS">LOSS</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="description" label="Description" rules={[{ required: true }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="estimatedAmount" label="Estimated Amount (AZN)">
            <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={actionOpen}
        title={
          actionType === 'approve'
            ? 'Approve Case'
            : actionType === 'reject'
            ? 'Reject Case'
            : 'Close Case'
        }
        onCancel={() => setActionOpen(false)}
        onOk={submitAction}
        okText={actionType === 'approve' ? 'Approve' : actionType === 'reject' ? 'Reject' : 'Close'}
        okButtonProps={{
          danger: actionType === 'reject',
        }}
      >
        {actionType === 'approve' && (
          <Form form={actionForm} layout="vertical">
            <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
              Confidential decision — deduction details visible to HR/Finance only.
            </Typography.Paragraph>
            <Form.Item
              name="deductionProposed"
              label="Propose deduction from salary"
              valuePropName="checked"
              initialValue={false}
            >
              <Input type="checkbox" />
            </Form.Item>
            <Form.Item
              noStyle
              shouldUpdate={(prev, curr) => prev.deductionProposed !== curr.deductionProposed}
            >
              {({ getFieldValue }) =>
                getFieldValue('deductionProposed') ? (
                  <Form.Item name="deductionAmount" label="Deduction Amount (AZN)">
                    <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
                  </Form.Item>
                ) : null
              }
            </Form.Item>
          </Form>
        )}
        {actionType === 'reject' && (
          <Form form={actionForm} layout="vertical">
            <Form.Item name="reason" label="Rejection Reason" rules={[{ required: true }]}>
              <Input.TextArea rows={3} />
            </Form.Item>
          </Form>
        )}
        {actionType === 'close' && (
          <Form form={actionForm} layout="vertical">
            <Form.Item name="notes" label="Closing Notes">
              <Input.TextArea rows={3} />
            </Form.Item>
          </Form>
        )}
      </Modal>
    </Card>
  )
}
