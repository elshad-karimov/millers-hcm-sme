import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  App as AntdApp,
  Descriptions,
  Drawer,
} from 'antd'
import { PlusOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import {
  returnToWorkApi,
  injuryReportsApi,
  type ReturnToWorkResponse,
  type ReturnToWorkStatus,
  type InjuryReportResponse,
  RETURN_TO_WORK_STATUS_OPTIONS,
} from '../api/ehs'
import { employeesApi, type Employee } from '../api/employees'

export function ReturnToWorkPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [plans, setPlans] = useState<ReturnToWorkResponse[]>([])
  const [filterStatus, setFilterStatus] = useState<ReturnToWorkStatus | undefined>()

  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const [editing, setEditing] = useState<ReturnToWorkResponse | null>(null)

  const [employees, setEmployees] = useState<Employee[]>([])
  const [injuries, setInjuries] = useState<InjuryReportResponse[]>([])

  const [selectedPlan, setSelectedPlan] = useState<ReturnToWorkResponse | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [approving, setApproving] = useState(false)

  useEffect(() => {
    loadPlans()
    loadEmployees()
    loadInjuries()
  }, [])

  const loadPlans = async () => {
    setLoading(true)
    try {
      const data = await returnToWorkApi.list()
      setPlans(data)
    } catch (err) {
      message.error('Failed to load return-to-work plans')
    } finally {
      setLoading(false)
    }
  }

  const loadEmployees = async () => {
    try {
      const data = await employeesApi.list()
      setEmployees(data.content)
    } catch {
      // non-critical
    }
  }

  const loadInjuries = async () => {
    try {
      const data = await injuryReportsApi.list()
      setInjuries(data)
    } catch {
      // non-critical
    }
  }

  const openCreateModal = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEditModal = (plan: ReturnToWorkResponse) => {
    setEditing(plan)
    form.setFieldsValue({
      injuryReportId: plan.injuryReportId,
      employeeId: plan.employeeId,
      medicalClearanceDate: dayjs(plan.medicalClearanceDate),
      restrictions: plan.restrictions,
      modifiedSchedule: plan.modifiedSchedule,
      status: plan.status,
    })
    setModalOpen(true)
  }

  const handleSubmit = async (values: any) => {
    setSubmitting(true)
    try {
      if (editing) {
        await returnToWorkApi.update(editing.id, {
          medicalClearanceDate: values.medicalClearanceDate.format('YYYY-MM-DD'),
          restrictions: values.restrictions || undefined,
          modifiedSchedule: values.modifiedSchedule || undefined,
          status: values.status || undefined,
        })
        message.success('Plan updated')
      } else {
        await returnToWorkApi.create({
          injuryReportId: values.injuryReportId,
          employeeId: values.employeeId,
          medicalClearanceDate: values.medicalClearanceDate.format('YYYY-MM-DD'),
          restrictions: values.restrictions || undefined,
          modifiedSchedule: values.modifiedSchedule || undefined,
        })
        message.success('Plan created')
      }
      setModalOpen(false)
      form.resetFields()
      loadPlans()
    } catch (err) {
      message.error((err as any)?.response?.data?.message || 'Failed to save plan')
    } finally {
      setSubmitting(false)
    }
  }

  const handleApproval = async (planId: string, approvalType: 'manager' | 'hr', approved: boolean) => {
    setApproving(true)
    try {
      if (approvalType === 'manager') {
        await returnToWorkApi.setManagerApproval(planId, { approved })
      } else {
        await returnToWorkApi.setHrApproval(planId, { approved })
      }
      message.success(approved ? 'Approved' : 'Rejected')
      if (selectedPlan?.id === planId) {
        const updated = await returnToWorkApi.get(planId)
        setSelectedPlan(updated)
      }
      loadPlans()
    } catch (err) {
      message.error((err as any)?.response?.data?.message || 'Failed to update approval')
    } finally {
      setApproving(false)
    }
  }

  const filteredPlans = filterStatus
    ? plans.filter((p) => p.status === filterStatus)
    : plans

  const columns: ColumnsType<ReturnToWorkResponse> = [
    {
      title: 'Employee',
      dataIndex: 'employeeName',
      key: 'employeeName',
      width: 200,
      render: (text, record) => (
        <a onClick={() => {
          setSelectedPlan(record)
          setDrawerOpen(true)
        }}>
          {text || '—'}
        </a>
      ),
    },
    {
      title: 'Clearance date',
      dataIndex: 'medicalClearanceDate',
      key: 'medicalClearanceDate',
      width: 130,
      sorter: (a, b) => a.medicalClearanceDate.localeCompare(b.medicalClearanceDate),
    },
    {
      title: 'Restrictions',
      dataIndex: 'restrictions',
      key: 'restrictions',
      ellipsis: true,
      render: (text) => text || '—',
    },
    {
      title: 'Manager',
      key: 'managerApproved',
      width: 100,
      render: (_, record) =>
        record.managerApproved === null ? (
          <Tag>Pending</Tag>
        ) : record.managerApproved ? (
          <Tag color="green">Approved</Tag>
        ) : (
          <Tag color="red">Rejected</Tag>
        ),
    },
    {
      title: 'HR',
      key: 'hrApproved',
      width: 100,
      render: (_, record) =>
        record.hrApproved === null ? (
          <Tag>Pending</Tag>
        ) : record.hrApproved ? (
          <Tag color="green">Approved</Tag>
        ) : (
          <Tag color="red">Rejected</Tag>
        ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (val: ReturnToWorkStatus) => (
        <Tag color={val === 'COMPLETED' ? 'green' : val === 'ACTIVE' ? 'blue' : 'default'}>
          {val}
        </Tag>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Space size="small">
          <Button size="small" onClick={() => openEditModal(record)}>
            Edit
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="Return-to-Work Plans"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            New plan
          </Button>
        }
      >
        <Space style={{ marginBottom: 16 }}>
          <Select
            placeholder="Filter by status"
            style={{ width: 180 }}
            allowClear
            value={filterStatus}
            onChange={setFilterStatus}
            options={RETURN_TO_WORK_STATUS_OPTIONS}
          />
        </Space>

        <Table
          loading={loading}
          dataSource={filteredPlans}
          columns={columns}
          rowKey="id"
          pagination={{ pageSize: 20 }}
        />
      </Card>

      {/* Create/Edit Modal */}
      <Modal
        title={editing ? 'Edit return-to-work plan' : 'New return-to-work plan'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          form.resetFields()
        }}
        footer={null}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          {!editing && (
            <>
              <Form.Item
                name="injuryReportId"
                label="Injury report"
                rules={[{ required: true }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={injuries.map((i) => ({
                    value: i.id,
                    label: `${i.employeeName || '—'} — ${i.injuryType}`,
                  }))}
                />
              </Form.Item>

              <Form.Item
                name="employeeId"
                label="Employee"
                rules={[{ required: true }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={employees.map((e) => ({
                    value: e.id,
                    label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
                  }))}
                />
              </Form.Item>
            </>
          )}

          <Form.Item
            name="medicalClearanceDate"
            label="Medical clearance date"
            rules={[{ required: true }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="restrictions" label="Restrictions">
            <Input.TextArea rows={3} />
          </Form.Item>

          <Form.Item name="modifiedSchedule" label="Modified schedule">
            <Input.TextArea rows={3} />
          </Form.Item>

          {editing && (
            <Form.Item name="status" label="Status">
              <Select options={RETURN_TO_WORK_STATUS_OPTIONS} />
            </Form.Item>
          )}

          <Form.Item style={{ marginBottom: 0 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={submitting}>
                {editing ? 'Update' : 'Create'}
              </Button>
              <Button
                onClick={() => {
                  setModalOpen(false)
                  form.resetFields()
                }}
              >
                Cancel
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Detail Drawer */}
      <Drawer
        title="Return-to-Work Plan"
        placement="right"
        width={600}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        {selectedPlan && (
          <div>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="Employee">
                {selectedPlan.employeeName || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Medical clearance date">
                {selectedPlan.medicalClearanceDate}
              </Descriptions.Item>
              <Descriptions.Item label="Restrictions">
                {selectedPlan.restrictions || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Modified schedule">
                {selectedPlan.modifiedSchedule || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Manager approval">
                {selectedPlan.managerApproved === null ? (
                  <Tag>Pending</Tag>
                ) : selectedPlan.managerApproved ? (
                  <Tag color="green">Approved</Tag>
                ) : (
                  <Tag color="red">Rejected</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="HR approval">
                {selectedPlan.hrApproved === null ? (
                  <Tag>Pending</Tag>
                ) : selectedPlan.hrApproved ? (
                  <Tag color="green">Approved</Tag>
                ) : (
                  <Tag color="red">Rejected</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={selectedPlan.status === 'COMPLETED' ? 'green' : 'blue'}>
                  {selectedPlan.status}
                </Tag>
              </Descriptions.Item>
            </Descriptions>

            <div style={{ marginTop: 24 }}>
              <Space direction="vertical" style={{ width: '100%' }}>
                {selectedPlan.managerApproved === null && (
                  <div>
                    <strong>Manager approval:</strong>
                    <div style={{ marginTop: 8 }}>
                      <Space>
                        <Button
                          type="primary"
                          icon={<CheckOutlined />}
                          onClick={() => handleApproval(selectedPlan.id, 'manager', true)}
                          loading={approving}
                        >
                          Approve
                        </Button>
                        <Button
                          danger
                          icon={<CloseOutlined />}
                          onClick={() => handleApproval(selectedPlan.id, 'manager', false)}
                          loading={approving}
                        >
                          Reject
                        </Button>
                      </Space>
                    </div>
                  </div>
                )}

                {selectedPlan.hrApproved === null && (
                  <div>
                    <strong>HR approval:</strong>
                    <div style={{ marginTop: 8 }}>
                      <Space>
                        <Button
                          type="primary"
                          icon={<CheckOutlined />}
                          onClick={() => handleApproval(selectedPlan.id, 'hr', true)}
                          loading={approving}
                        >
                          Approve (HR)
                        </Button>
                        <Button
                          danger
                          icon={<CloseOutlined />}
                          onClick={() => handleApproval(selectedPlan.id, 'hr', false)}
                          loading={approving}
                        >
                          Reject (HR)
                        </Button>
                      </Space>
                    </div>
                  </div>
                )}
              </Space>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  )
}
