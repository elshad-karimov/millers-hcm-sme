import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  App as AntdApp,
} from 'antd'
import { PlusOutlined, RollbackOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import {
  ppeAssignmentsApi,
  ppeItemsApi,
  type PpeAssignmentResponse,
  type PpeItemResponse,
} from '../api/ehs'
import { employeesApi, type Employee } from '../api/employees'

export function PpeAssignmentsPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [assignments, setAssignments] = useState<PpeAssignmentResponse[]>([])
  const [expiringAssignments, setExpiringAssignments] = useState<PpeAssignmentResponse[]>([])
  const [filterEmployee, setFilterEmployee] = useState<string | undefined>()

  const [issueModalOpen, setIssueModalOpen] = useState(false)
  const [issueForm] = Form.useForm()
  const [issuing, setIssuing] = useState(false)

  const [returnModalOpen, setReturnModalOpen] = useState(false)
  const [returnForm] = Form.useForm()
  const [returning, setReturning] = useState(false)
  const [selectedAssignment, setSelectedAssignment] = useState<PpeAssignmentResponse | null>(null)

  const [employees, setEmployees] = useState<Employee[]>([])
  const [ppeItems, setPpeItems] = useState<PpeItemResponse[]>([])

  useEffect(() => {
    loadAssignments()
    loadExpiringAssignments()
    loadEmployees()
    loadPpeItems()
  }, [])

  const loadAssignments = async () => {
    setLoading(true)
    try {
      const data = await ppeAssignmentsApi.list()
      setAssignments(data)
    } catch (err) {
      message.error('Failed to load PPE assignments')
    } finally {
      setLoading(false)
    }
  }

  const loadExpiringAssignments = async () => {
    try {
      const data = await ppeAssignmentsApi.listExpiring()
      setExpiringAssignments(data)
    } catch {
      // non-critical
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

  const loadPpeItems = async () => {
    try {
      const data = await ppeItemsApi.list(true)
      setPpeItems(data)
    } catch {
      // non-critical
    }
  }

  const handleIssue = async (values: any) => {
    setIssuing(true)
    try {
      await ppeAssignmentsApi.issue({
        employeeId: values.employeeId,
        ppeItemId: values.ppeItemId,
        issuedAt: values.issuedAt.format('YYYY-MM-DD'),
        expiryDate: values.expiryDate?.format('YYYY-MM-DD') || undefined,
        conditionAtIssue: values.conditionAtIssue || undefined,
        notes: values.notes || undefined,
      })
      message.success('PPE issued')
      setIssueModalOpen(false)
      issueForm.resetFields()
      loadAssignments()
      loadExpiringAssignments()
    } catch (err) {
      message.error((err as any)?.response?.data?.message || 'Failed to issue PPE')
    } finally {
      setIssuing(false)
    }
  }

  const handleReturn = async (values: any) => {
    if (!selectedAssignment) return
    setReturning(true)
    try {
      await ppeAssignmentsApi.returnPpe(selectedAssignment.id, {
        returnedAt: values.returnedAt.format('YYYY-MM-DD'),
        conditionAtReturn: values.conditionAtReturn || undefined,
      })
      message.success('PPE returned')
      setReturnModalOpen(false)
      returnForm.resetFields()
      loadAssignments()
      loadExpiringAssignments()
    } catch (err) {
      message.error((err as any)?.response?.data?.message || 'Failed to return PPE')
    } finally {
      setReturning(false)
    }
  }

  const filteredAssignments = filterEmployee
    ? assignments.filter((a) => a.employeeId === filterEmployee)
    : assignments

  const activeAssignments = filteredAssignments.filter((a) => !a.returnedAt)

  const columns: ColumnsType<PpeAssignmentResponse> = [
    {
      title: 'Employee',
      dataIndex: 'employeeName',
      key: 'employeeName',
      width: 200,
      render: (text) => text || '—',
    },
    {
      title: 'PPE item',
      dataIndex: 'ppeItemName',
      key: 'ppeItemName',
      width: 200,
      render: (text) => text || '—',
    },
    {
      title: 'Issued',
      dataIndex: 'issuedAt',
      key: 'issuedAt',
      width: 110,
      sorter: (a, b) => a.issuedAt.localeCompare(b.issuedAt),
    },
    {
      title: 'Expiry',
      dataIndex: 'expiryDate',
      key: 'expiryDate',
      width: 110,
      render: (text) => {
        if (!text) return '—'
        const isExpired = dayjs(text).isBefore(dayjs(), 'day')
        return isExpired ? <Tag color="red">{text}</Tag> : text
      },
    },
    {
      title: 'Returned',
      dataIndex: 'returnedAt',
      key: 'returnedAt',
      width: 110,
      render: (text) => text || '—',
    },
    {
      title: 'Condition (issue)',
      dataIndex: 'conditionAtIssue',
      key: 'conditionAtIssue',
      width: 140,
      render: (text) => text || '—',
    },
    {
      title: 'Action',
      key: 'action',
      width: 100,
      render: (_, record) =>
        !record.returnedAt ? (
          <Button
            size="small"
            icon={<RollbackOutlined />}
            onClick={() => {
              setSelectedAssignment(record)
              returnForm.setFieldsValue({ returnedAt: dayjs() })
              setReturnModalOpen(true)
            }}
          >
            Return
          </Button>
        ) : null,
    },
  ]

  const expiringColumns: ColumnsType<PpeAssignmentResponse> = [
    {
      title: 'Employee',
      dataIndex: 'employeeName',
      key: 'employeeName',
      width: 200,
    },
    {
      title: 'PPE item',
      dataIndex: 'ppeItemName',
      key: 'ppeItemName',
      width: 200,
    },
    {
      title: 'Expiry date',
      dataIndex: 'expiryDate',
      key: 'expiryDate',
      width: 110,
      render: (text) => <Tag color="orange">{text}</Tag>,
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="PPE Assignments"
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setIssueModalOpen(true)}
          >
            Issue PPE
          </Button>
        }
      >
        <Tabs
          items={[
            {
              key: 'all',
              label: `All (${activeAssignments.length})`,
              children: (
                <div>
                  <Space style={{ marginBottom: 16 }}>
                    <Select
                      placeholder="Filter by employee"
                      style={{ width: 280 }}
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      value={filterEmployee}
                      onChange={setFilterEmployee}
                      options={employees.map((e) => ({
                        value: e.id,
                        label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
                      }))}
                    />
                  </Space>

                  <Table
                    loading={loading}
                    dataSource={activeAssignments}
                    columns={columns}
                    rowKey="id"
                    pagination={{ pageSize: 20 }}
                  />
                </div>
              ),
            },
            {
              key: 'expiring',
              label: `Expiring soon (${expiringAssignments.length})`,
              children: (
                <Table
                  loading={loading}
                  dataSource={expiringAssignments}
                  columns={expiringColumns}
                  rowKey="id"
                  pagination={{ pageSize: 20 }}
                />
              ),
            },
          ]}
        />
      </Card>

      {/* Issue PPE Modal */}
      <Modal
        title="Issue PPE"
        open={issueModalOpen}
        onCancel={() => {
          setIssueModalOpen(false)
          issueForm.resetFields()
        }}
        footer={null}
        width={600}
      >
        <Form
          form={issueForm}
          layout="vertical"
          onFinish={handleIssue}
          initialValues={{ issuedAt: dayjs() }}
        >
          <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
            />
          </Form.Item>

          <Form.Item name="ppeItemId" label="PPE item" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={ppeItems.map((p) => ({
                value: p.id,
                label: `${p.code} — ${p.name}`,
              }))}
            />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="issuedAt" label="Issue date" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="expiryDate" label="Expiry date">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="conditionAtIssue" label="Condition at issue">
            <Input />
          </Form.Item>

          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={3} />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={issuing}>
                Issue
              </Button>
              <Button onClick={() => { setIssueModalOpen(false); issueForm.resetFields() }}>
                Cancel
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Return PPE Modal */}
      <Modal
        title="Return PPE"
        open={returnModalOpen}
        onCancel={() => {
          setReturnModalOpen(false)
          returnForm.resetFields()
        }}
        footer={null}
      >
        <Form form={returnForm} layout="vertical" onFinish={handleReturn}>
          <Form.Item name="returnedAt" label="Return date" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="conditionAtReturn" label="Condition at return">
            <Input />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={returning}>
                Return
              </Button>
              <Button onClick={() => { setReturnModalOpen(false); returnForm.resetFields() }}>
                Cancel
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
