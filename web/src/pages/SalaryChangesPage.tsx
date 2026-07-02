import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
  Alert,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  compensationApi,
  type SalaryChangeRequestDto,
  type SalaryChangeStatus,
  type ChangeReasonResponse,
  type CreateSalaryChangeRequest,
  type CompensationProfileDto,
} from '../api/compensation'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const STATUS_COLOR: Record<SalaryChangeStatus, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'cyan',
  APPLIED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}

interface FormValues {
  employeeId: string
  proposedSalary: number
  currency: string
  changeReasonId: string
  effectiveDate: string
  note?: string
}

export function SalaryChangesPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [rows, setRows] = useState<SalaryChangeRequestDto[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [changeReasons, setChangeReasons] = useState<ChangeReasonResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [statusFilter, setStatusFilter] = useState<SalaryChangeStatus | undefined>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [form] = Form.useForm<FormValues>()

  const [loadingEmployees, setLoadingEmployees] = useState(false)
  const [selectedProfile, setSelectedProfile] = useState<CompensationProfileDto | null>(null)
  const [loadingProfile, setLoadingProfile] = useState(false)

  const selectedEmployeeId = Form.useWatch('employeeId', form)
  const proposedSalary = Form.useWatch('proposedSalary', form)

  const load = () => {
    setLoading(true)
    compensationApi
      .listSalaryChanges({ status: statusFilter })
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load salary changes'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    compensationApi
      .listChangeReasons()
      .then((reasons) => setChangeReasons(reasons.filter((r) => r.isActive)))
      .catch(() => message.error('Failed to load change reasons'))
  }, [message])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter])

  // Load employee profile when selected
  useEffect(() => {
    if (selectedEmployeeId) {
      setLoadingProfile(true)
      compensationApi
        .getProfile(selectedEmployeeId)
        .then(setSelectedProfile)
        .catch(() => {
          setSelectedProfile(null)
          message.warning('Could not load employee compensation profile')
        })
        .finally(() => setLoadingProfile(false))
    } else {
      setSelectedProfile(null)
    }
  }, [selectedEmployeeId, message])

  const handleSearchEmployees = (search: string) => {
    if (search.length < 2) {
      setEmployees([])
      return
    }
    setLoadingEmployees(true)
    employeesApi
      .list({ search, size: 50 })
      .then((resp) => setEmployees(resp.content))
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to search employees'),
      )
      .finally(() => setLoadingEmployees(false))
  }

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({ currency: 'AZN', effectiveDate: dayjs().add(1, 'month').format('YYYY-MM-DD') })
    setSelectedProfile(null)
    setDrawerOpen(true)
  }

  const submit = async (v: FormValues) => {
    const payload: CreateSalaryChangeRequest = {
      employeeId: v.employeeId,
      proposedSalary: v.proposedSalary,
      currency: v.currency,
      changeReasonId: v.changeReasonId,
      effectiveDate: v.effectiveDate,
      note: v.note,
    }

    try {
      await compensationApi.createSalaryChange(payload)
      message.success('Salary change request created (DRAFT)')
      setDrawerOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Create failed',
      )
    }
  }

  const handleSubmit = (rec: SalaryChangeRequestDto) => {
    modal.confirm({
      title: 'Submit for Approval?',
      content: `Submit salary change request for approval workflow?`,
      okText: 'Submit',
      onOk: async () => {
        try {
          await compensationApi.submitSalaryChange(rec.id)
          message.success('Submitted for approval. Check the Approvals inbox to approve/reject.')
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Submit failed',
          )
        }
      },
    })
  }

  const handleCancel = (rec: SalaryChangeRequestDto) => {
    modal.confirm({
      title: 'Cancel Request?',
      content: 'Cancel this salary change request?',
      okText: 'Cancel Request',
      onOk: async () => {
        try {
          await compensationApi.cancelSalaryChange(rec.id)
          message.success('Salary change request cancelled')
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Cancel failed',
          )
        }
      },
    })
  }

  const handleGenerateLetter = async (rec: SalaryChangeRequestDto) => {
    try {
      const result = await compensationApi.generateSalaryLetter(rec.id)
      message.success('Salary increase letter generated')
      // Open the PDF in a new window
      window.open(result.downloadPath, '_blank')
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Letter generation failed',
      )
    }
  }

  const employeeMap = new Map(employees.map((e) => [e.id, e]))

  const columns: ColumnsType<SalaryChangeRequestDto> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        return e ? `${e.employeeNo} - ${e.firstName} ${e.lastName}` : id
      },
    },
    {
      title: 'Current Salary',
      dataIndex: 'currentSalary',
      align: 'right',
      render: (v: number, rec) => `${v.toLocaleString()} ${rec.currency}`,
    },
    {
      title: 'Proposed Salary',
      dataIndex: 'proposedSalary',
      align: 'right',
      render: (v: number, rec) => `${v.toLocaleString()} ${rec.currency}`,
    },
    {
      title: 'Increase %',
      dataIndex: 'increasePct',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)}%`,
    },
    {
      title: 'Reason',
      dataIndex: 'changeReasonId',
      render: (id: string) => {
        const reason = changeReasons.find((r) => r.id === id)
        return reason ? reason.name : id
      },
    },
    {
      title: 'Effective Date',
      dataIndex: 'effectiveDate',
      width: 120,
    },
    {
      title: 'Flags',
      width: 120,
      render: (_, rec) => (
        <Space direction="vertical" size={2}>
          {rec.outsideBand && <Tag color="red" style={{ fontSize: 10 }}>Out-of-Band</Tag>}
          {rec.aboveThreshold && <Tag color="red" style={{ fontSize: 10 }}>Above-Threshold</Tag>}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 140,
      render: (s: SalaryChangeStatus) => <Tag color={STATUS_COLOR[s]}>{s.replace('_', ' ')}</Tag>,
    },
    {
      title: 'Actions',
      width: 240,
      render: (_, rec) => {
        const canSubmit = rec.status === 'DRAFT' && canWrite
        const canCancelReq = (rec.status === 'DRAFT' || rec.status === 'PENDING_APPROVAL') && canWrite
        const canGenerateLetter = rec.status === 'APPLIED' && canWrite
        return (
          <Space>
            {canSubmit && (
              <Button size="small" type="primary" onClick={() => handleSubmit(rec)}>
                Submit
              </Button>
            )}
            {canCancelReq && (
              <Button size="small" onClick={() => handleCancel(rec)}>
                Cancel
              </Button>
            )}
            {canGenerateLetter && (
              <Button size="small" type="default" onClick={() => handleGenerateLetter(rec)}>
                Generate Letter
              </Button>
            )}
          </Space>
        )
      },
    },
  ]

  const currentSalary = selectedProfile?.currentSalary?.monthlyBaseSalary ?? 0
  const increaseHint =
    currentSalary > 0 && proposedSalary > 0
      ? (((proposedSalary - currentSalary) / currentSalary) * 100).toFixed(2)
      : null

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Title level={2}>Salary Change Requests</Title>
        {canWrite && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            New Salary Change
          </Button>
        )}
      </div>

      <Alert
        type="info"
        message="Approval happens in the Approvals inbox. Submit a request, then approve/reject it from the Inbox page."
        style={{ marginBottom: 16 }}
        closable
      />

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Select
            placeholder="Filter by status"
            style={{ width: 180 }}
            allowClear
            value={statusFilter}
            onChange={setStatusFilter}
            options={[
              { value: 'DRAFT', label: 'Draft' },
              { value: 'PENDING_APPROVAL', label: 'Pending Approval' },
              { value: 'APPROVED', label: 'Approved' },
              { value: 'REJECTED', label: 'Rejected' },
              { value: 'APPLIED', label: 'Applied' },
              { value: 'CANCELLED', label: 'Cancelled' },
            ]}
          />
        </Space>
        <Table
          dataSource={rows}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Drawer
        title="New Salary Change Request"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select
              showSearch
              placeholder="Type employee name or number..."
              filterOption={false}
              onSearch={handleSearchEmployees}
              loading={loadingEmployees}
              notFoundContent={loadingEmployees ? 'Loading...' : 'Type to search'}
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
            />
          </Form.Item>

          {loadingProfile && <Text type="secondary">Loading employee profile...</Text>}

          {selectedProfile?.currentSalary && (
            <div style={{ marginBottom: 16, padding: 12, background: '#f5f5f5', borderRadius: 4 }}>
              <Text strong>Current Salary: </Text>
              <Text>
                {selectedProfile.currentSalary.monthlyBaseSalary.toLocaleString()}{' '}
                {selectedProfile.currentSalary.currency}
              </Text>
              {selectedProfile.band && (
                <div style={{ marginTop: 4 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Band: {selectedProfile.band.code} ({selectedProfile.band.minSalary.toLocaleString()} –{' '}
                    {selectedProfile.band.maxSalary.toLocaleString()})
                  </Text>
                </div>
              )}
            </div>
          )}

          <Form.Item
            name="proposedSalary"
            label="Proposed Salary"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>

          {increaseHint && parseFloat(increaseHint) > 15 && (
            <Alert
              type="warning"
              message={`Increase of ${increaseHint}% is >15%. Approval or exception may be required.`}
              style={{ marginBottom: 12 }}
            />
          )}

          <Form.Item
            name="currency"
            label="Currency"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select
              options={[
                { value: 'AZN', label: 'AZN' },
                { value: 'USD', label: 'USD' },
                { value: 'EUR', label: 'EUR' },
              ]}
            />
          </Form.Item>

          <Form.Item
            name="changeReasonId"
            label="Change Reason"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select
              options={changeReasons.map((r) => ({ value: r.id, label: `${r.code} — ${r.name}` }))}
            />
          </Form.Item>

          <Form.Item
            name="effectiveDate"
            label="Effective Date"
            rules={[{ required: true, message: 'Required' }]}
            getValueProps={(value) => ({ value: value ? dayjs(value) : undefined })}
            getValueFromEvent={(date) => (date ? date.format('YYYY-MM-DD') : undefined)}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="note" label="Note (optional)">
            <Input.TextArea rows={3} />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Create
              </Button>
              <Button onClick={() => setDrawerOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
