import { useEffect, useState } from 'react'
import { Card, Button, Modal, Form, Select, Input, Table, Tag, App as AntdApp } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  hrRequestsApi,
  type HrServiceRequest,
  type ServiceRequestCategory,
  type ServiceRequestPriority,
  type ServiceRequestStatus,
  REQUEST_CATEGORY_COLOR,
  REQUEST_STATUS_COLOR,
  REQUEST_PRIORITY_COLOR,
} from '../api/hrRequests'

const { TextArea } = Input

export function HrRequestsWidget() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [requests, setRequests] = useState<HrServiceRequest[]>([])
  const [showModal, setShowModal] = useState(false)
  const [form] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const res = await hrRequestsApi.myRequests()
      setRequests(res.data)
    } catch (err: any) {
      message.error('Failed to load HR requests: ' + (err.message || ''))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      await hrRequestsApi.submit(values)
      message.success('Request submitted')
      form.resetFields()
      setShowModal(false)
      load()
    } catch (err: any) {
      message.error('Failed to submit request: ' + (err.message || ''))
    }
  }

  const columns: ColumnsType<HrServiceRequest> = [
    {
      title: 'Request No',
      dataIndex: 'requestNo',
      width: 120,
    },
    {
      title: 'Category',
      dataIndex: 'category',
      width: 150,
      render: (cat: ServiceRequestCategory) => (
        <Tag color={REQUEST_CATEGORY_COLOR[cat]}>{cat.replace(/_/g, ' ')}</Tag>
      ),
    },
    {
      title: 'Subject',
      dataIndex: 'subject',
    },
    {
      title: 'Priority',
      dataIndex: 'priority',
      width: 100,
      render: (p: ServiceRequestPriority) => (
        <Tag color={REQUEST_PRIORITY_COLOR[p]}>{p}</Tag>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: ServiceRequestStatus) => <Tag color={REQUEST_STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'SLA Due',
      dataIndex: 'slaDue',
      width: 120,
      render: (d) => (d ? new Date(d).toLocaleDateString() : '—'),
    },
  ]

  return (
    <Card
      title="HR Requests"
      size="small"
      extra={<Button type="primary" size="small" onClick={() => setShowModal(true)}>New Request</Button>}
    >
      <Table
        size="small"
        loading={loading}
        dataSource={requests}
        columns={columns}
        rowKey="id"
        pagination={{ pageSize: 5, showSizeChanger: false }}
      />
      <Modal
        title="Submit HR Service Request"
        open={showModal}
        onCancel={() => {
          setShowModal(false)
          form.resetFields()
        }}
        onOk={handleSubmit}
        okText="Submit"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="category"
            label="Category"
            rules={[{ required: true, message: 'Please select category' }]}
          >
            <Select>
              <Select.Option value="SALARY_CERT">Salary Certificate</Select.Option>
              <Select.Option value="EMPLOYMENT_LETTER">Employment Letter</Select.Option>
              <Select.Option value="PAYROLL_INQUIRY">Payroll Inquiry</Select.Option>
              <Select.Option value="POLICY_QUESTION">Policy Question</Select.Option>
              <Select.Option value="GRIEVANCE">Grievance</Select.Option>
              <Select.Option value="OTHER">Other</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="priority"
            label="Priority"
            initialValue="NORMAL"
          >
            <Select>
              <Select.Option value="LOW">Low</Select.Option>
              <Select.Option value="NORMAL">Normal</Select.Option>
              <Select.Option value="HIGH">High</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="subject"
            label="Subject"
            rules={[{ required: true, message: 'Please enter subject' }]}
          >
            <Input maxLength={300} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <TextArea rows={4} maxLength={4000} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
