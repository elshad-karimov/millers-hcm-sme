// M461-M464 — Loan request management + dashboard (HR/Payroll approval).

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Drawer,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  App as AntdApp,
  Collapse,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../api/client'
import { employeesApi, type Employee } from '../api/employees'
import dayjs from 'dayjs'

type LoanRequestStatus =
  | 'SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'DISBURSED'

interface LoanRequest {
  id: string
  requestNo: string
  employeeId: string
  employeeNo: string
  employeeName: string
  loanTypeId: string
  loanTypeName: string
  requestedAmount: number
  requestedMonths: number
  approvedAmount?: number
  approvedMonths?: number
  purpose?: string
  status: LoanRequestStatus
  eligibilityNotes?: string
  rejectionReason?: string
  submittedAt: string
  approvedAt?: string
}

interface InstallmentSchedule {
  id: string
  installmentNumber: number
  dueYear: number
  dueMonth: number
  installmentAmount: number
  principalPortion: number
  interestPortion: number
  remainingBalance: number
  status: string
  paidAt?: string
}

interface LoanDashboard {
  activeLoans: number
  totalOutstanding: number
  completionPct: number
  byDepartment: {
    departmentName: string
    employeeCount: number
    totalOutstanding: number
  }[]
  overdueList: {
    requestNo: string
    employeeNo: string
    employeeName: string
    installmentNumber: number
    installmentAmount: number
    remainingBalance: number
  }[]
}

const REQUEST_STATUS_COLOR: Record<LoanRequestStatus, string> = {
  SUBMITTED: 'orange',
  APPROVED: 'blue',
  REJECTED: 'red',
  CANCELLED: 'default',
  DISBURSED: 'green',
}

export function LoanRequestsPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<LoanRequest[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(false)
  const [filterStatus, setFilterStatus] = useState<LoanRequestStatus | undefined>()
  const [scheduleOpen, setScheduleOpen] = useState(false)
  const [schedule, setSchedule] = useState<InstallmentSchedule[]>([])
  const [scheduleLoading, setScheduleLoading] = useState(false)
  const [currentRequest, setCurrentRequest] = useState<LoanRequest | null>(null)
  const [dashboard, setDashboard] = useState<LoanDashboard | null>(null)

  const load = () => {
    setLoading(true)
    api
      .get<LoanRequest[]>('/payroll/loan-requests')
      .then((r) => setRows(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load requests'))
      .finally(() => setLoading(false))
  }

  const loadDashboard = () => {
    api
      .get<LoanDashboard>('/reports/loans/dashboard')
      .then(setDashboard)
      .catch(() => {})
  }

  useEffect(() => {
    load()
    loadDashboard()
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content)).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleAction = async (
    id: string,
    action: 'approve' | 'reject' | 'cancel',
    reason?: string,
  ) => {
    try {
      if (action === 'reject') {
        const rejectionReason = reason || prompt('Rejection reason:')
        if (!rejectionReason) return
        await api.post(`/payroll/loan-requests/${id}/reject`, { reason: rejectionReason })
      } else {
        await api.post(`/payroll/loan-requests/${id}/${action}`)
      }
      message.success(`Request ${action}ed`)
      load()
      loadDashboard()
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? `${action} failed`)
    }
  }

  const openSchedule = (request: LoanRequest) => {
    setCurrentRequest(request)
    setScheduleLoading(true)
    setScheduleOpen(true)
    api
      .get<InstallmentSchedule[]>(`/payroll/loan-installments/loan-requests/${request.id}`)
      .then(setSchedule)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load schedule'))
      .finally(() => setScheduleLoading(false))
  }

  const filteredRows = filterStatus
    ? rows.filter((r) => r.status === filterStatus)
    : rows

  const columns: ColumnsType<LoanRequest> = [
    { title: 'Request No', dataIndex: 'requestNo', width: 120 },
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
      title: 'Loan Type',
      dataIndex: 'loanTypeName',
      ellipsis: true,
    },
    {
      title: 'Requested',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{r.requestedAmount.toFixed(2)} AZN</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {r.requestedMonths} months
          </Typography.Text>
        </Space>
      ),
      width: 130,
      align: 'right',
    },
    {
      title: 'Approved',
      render: (_, r) =>
        r.approvedAmount ? (
          <Space direction="vertical" size={0}>
            <Typography.Text>{r.approvedAmount.toFixed(2)} AZN</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {r.approvedMonths} months
            </Typography.Text>
          </Space>
        ) : (
          '—'
        ),
      width: 130,
      align: 'right',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: LoanRequestStatus) => (
        <Tag color={REQUEST_STATUS_COLOR[s]}>{s}</Tag>
      ),
    },
    {
      title: 'Submitted',
      dataIndex: 'submittedAt',
      width: 110,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: 'Actions',
      width: 240,
      render: (_, r) => (
        <Space size="small">
          {r.eligibilityNotes && (
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {r.eligibilityNotes}
            </Typography.Text>
          )}
          {r.status === 'SUBMITTED' && (
            <>
              <a onClick={() => handleAction(r.id, 'approve')}>Approve</a>
              <a onClick={() => handleAction(r.id, 'reject')}>Reject</a>
            </>
          )}
          {r.status === 'SUBMITTED' && (
            <a onClick={() => handleAction(r.id, 'cancel')}>Cancel</a>
          )}
          {(r.status === 'APPROVED' || r.status === 'DISBURSED') && (
            <a onClick={() => openSchedule(r)}>Schedule</a>
          )}
        </Space>
      ),
    },
  ]

  const scheduleColumns: ColumnsType<InstallmentSchedule> = [
    { title: '#', dataIndex: 'installmentNumber', width: 50 },
    {
      title: 'Due',
      render: (_, r) => `${r.dueYear}/${String(r.dueMonth).padStart(2, '0')}`,
      width: 100,
    },
    {
      title: 'Amount',
      dataIndex: 'installmentAmount',
      align: 'right',
      render: (v: number) => v.toFixed(2),
    },
    {
      title: 'Principal',
      dataIndex: 'principalPortion',
      align: 'right',
      render: (v: number) => v.toFixed(2),
    },
    {
      title: 'Interest',
      dataIndex: 'interestPortion',
      align: 'right',
      render: (v: number) => v.toFixed(2),
    },
    {
      title: 'Balance',
      dataIndex: 'remainingBalance',
      align: 'right',
      render: (v: number) => v.toFixed(2),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: string) => <Tag color={s === 'PAID' ? 'green' : 'orange'}>{s}</Tag>,
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Typography.Title level={3} style={{ margin: 0, marginBottom: 16 }}>
        Loans Management
      </Typography.Title>

      {dashboard && (
        <Collapse
          items={[
            {
              key: 'dashboard',
              label: 'Loan Dashboard',
              children: (
                <Space direction="vertical" style={{ width: '100%' }} size="large">
                  <Row gutter={16}>
                    <Col xs={24} sm={8}>
                      <Card size="small">
                        <Statistic
                          title="Active Loans"
                          value={dashboard.activeLoans}
                          valueStyle={{ color: '#1677ff' }}
                        />
                      </Card>
                    </Col>
                    <Col xs={24} sm={8}>
                      <Card size="small">
                        <Statistic
                          title="Total Outstanding"
                          value={dashboard.totalOutstanding}
                          precision={2}
                          suffix="AZN"
                          valueStyle={{ color: '#cf1322' }}
                        />
                      </Card>
                    </Col>
                    <Col xs={24} sm={8}>
                      <Card size="small">
                        <Statistic
                          title="Completion %"
                          value={dashboard.completionPct}
                          precision={1}
                          suffix="%"
                          valueStyle={{ color: '#3f8600' }}
                        />
                      </Card>
                    </Col>
                  </Row>

                  <Card title="Outstanding by Department" size="small">
                    <Table
                      rowKey="departmentName"
                      size="small"
                      pagination={false}
                      dataSource={dashboard.byDepartment}
                      columns={[
                        { title: 'Department', dataIndex: 'departmentName' },
                        {
                          title: 'Employees',
                          dataIndex: 'employeeCount',
                          align: 'right',
                        },
                        {
                          title: 'Total Outstanding',
                          dataIndex: 'totalOutstanding',
                          align: 'right',
                          render: (v: number) => `${v.toFixed(2)} AZN`,
                        },
                      ]}
                    />
                  </Card>

                  {dashboard.overdueList.length > 0 && (
                    <Card title="Overdue Installments" size="small">
                      <Table
                        rowKey={(r) => `${r.requestNo}-${r.installmentNumber}`}
                        size="small"
                        pagination={false}
                        dataSource={dashboard.overdueList}
                        columns={[
                          { title: 'Request', dataIndex: 'requestNo', width: 100 },
                          { title: 'Employee', dataIndex: 'employeeName' },
                          {
                            title: 'Installment #',
                            dataIndex: 'installmentNumber',
                            width: 100,
                          },
                          {
                            title: 'Amount',
                            dataIndex: 'installmentAmount',
                            align: 'right',
                            render: (v: number) => v.toFixed(2),
                          },
                          {
                            title: 'Remaining',
                            dataIndex: 'remainingBalance',
                            align: 'right',
                            render: (v: number) => v.toFixed(2),
                          },
                        ]}
                      />
                    </Card>
                  )}
                </Space>
              ),
            },
          ]}
          defaultActiveKey={[]}
          style={{ marginBottom: 16 }}
        />
      )}

      <Card size="small">
        <Space style={{ marginBottom: 12 }}>
          <Select
            allowClear
            placeholder="Filter by status"
            style={{ width: 200 }}
            value={filterStatus}
            onChange={setFilterStatus}
            options={[
              { value: 'SUBMITTED', label: 'SUBMITTED' },
              { value: 'APPROVED', label: 'APPROVED' },
              { value: 'REJECTED', label: 'REJECTED' },
              { value: 'CANCELLED', label: 'CANCELLED' },
              { value: 'DISBURSED', label: 'DISBURSED' },
            ]}
          />
        </Space>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={filteredRows}
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Drawer
        open={scheduleOpen}
        onClose={() => setScheduleOpen(false)}
        width={720}
        title={currentRequest ? `Installment Schedule — ${currentRequest.requestNo}` : ''}
      >
        {currentRequest && (
          <>
            <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
              Employee: <strong>{currentRequest.employeeName}</strong> ({currentRequest.employeeNo})
              <br />
              Approved: <strong>{currentRequest.approvedAmount?.toFixed(2)} AZN</strong> over{' '}
              <strong>{currentRequest.approvedMonths} months</strong>
            </Typography.Paragraph>
            <Table
              rowKey="id"
              size="small"
              columns={scheduleColumns}
              dataSource={schedule}
              loading={scheduleLoading}
              pagination={false}
            />
          </>
        )}
      </Drawer>
    </div>
  )
}
