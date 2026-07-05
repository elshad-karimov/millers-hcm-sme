import { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Tag,
  Button,
  Space,
  Select,
  Modal,
  Form,
  Input,
  Checkbox,
  Typography,
  App as AntdApp,
  Drawer,
  Divider,
  List,
  Switch,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  hrServiceQueueApi,
  type HrServiceRequest,
  type HrServiceRequestComment,
  type HrAgentQueue,
  type ServiceRequestCategory,
  type ServiceRequestStatus,
  type ServiceRequestPriority,
  REQUEST_CATEGORY_COLOR,
  REQUEST_STATUS_COLOR,
  REQUEST_PRIORITY_COLOR,
} from '../api/hrRequests'

const { TextArea } = Input

export default function HrServiceQueuePage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [requests, setRequests] = useState<HrServiceRequest[]>([])
  const [queues, setQueues] = useState<HrAgentQueue[]>([])
  const [selectedQueueId, setSelectedQueueId] = useState<string | undefined>()
  const [categoryFilter, setCategoryFilter] = useState<ServiceRequestCategory | undefined>()
  const [statusFilter, setStatusFilter] = useState<ServiceRequestStatus | undefined>()
  const [overdueOnly, setOverdueOnly] = useState(false)
  const [selectedRequest, setSelectedRequest] = useState<HrServiceRequest | null>(null)
  const [action, setAction] = useState<'assign' | 'resolve' | 'reassign' | null>(null)
  const [viewDrawerOpen, setViewDrawerOpen] = useState(false)
  const [comments, setComments] = useState<HrServiceRequestComment[]>([])
  const [commentBody, setCommentBody] = useState('')
  const [isInternalComment, setIsInternalComment] = useState(false)
  const [form] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const [reqRes, queueRes] = await Promise.all([
        hrServiceQueueApi.queue({
          category: categoryFilter,
          status: statusFilter,
          overdue: overdueOnly,
        }),
        hrServiceQueueApi.listQueues(),
      ])
      let allRequests = reqRes.data
      // Filter by queue if selected
      if (selectedQueueId) {
        allRequests = allRequests.filter(r => r.queueId === selectedQueueId)
      }
      setRequests(allRequests)
      setQueues(queueRes.data)
    } catch (err: any) {
      message.error('Failed to load queue: ' + (err.message || ''))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [categoryFilter, statusFilter, overdueOnly, selectedQueueId])

  const handleAssign = async () => {
    if (!selectedRequest) return
    try {
      const values = await form.validateFields()
      await hrServiceQueueApi.assign(selectedRequest.id, values.assignToUsername)
      message.success('Request assigned')
      setAction(null)
      setSelectedRequest(null)
      form.resetFields()
      load()
    } catch (err: any) {
      message.error('Failed to assign: ' + (err.message || ''))
    }
  }

  const handleStart = async (id: string) => {
    try {
      await hrServiceQueueApi.start(id)
      message.success('Request started')
      load()
    } catch (err: any) {
      message.error('Failed to start: ' + (err.message || ''))
    }
  }

  const handleResolve = async () => {
    if (!selectedRequest) return
    try {
      const values = await form.validateFields()
      await hrServiceQueueApi.resolve(selectedRequest.id, values.resolutionNotes)
      message.success('Request resolved')
      setAction(null)
      setSelectedRequest(null)
      form.resetFields()
      load()
    } catch (err: any) {
      message.error('Failed to resolve: ' + (err.message || ''))
    }
  }

  const handleReassign = async () => {
    if (!selectedRequest) return
    try {
      const values = await form.validateFields()
      await hrServiceQueueApi.reassign(selectedRequest.id, values.queueId)
      message.success('Request reassigned')
      setAction(null)
      setSelectedRequest(null)
      form.resetFields()
      load()
    } catch (err: any) {
      message.error('Failed to reassign: ' + (err.message || ''))
    }
  }

  const handleClose = async (id: string) => {
    try {
      await hrServiceQueueApi.close(id)
      message.success('Request closed')
      load()
    } catch (err: any) {
      message.error('Failed to close: ' + (err.message || ''))
    }
  }

  const handleReopen = async (id: string) => {
    try {
      await hrServiceQueueApi.reopen(id)
      message.success('Request reopened')
      load()
    } catch (err: any) {
      message.error('Failed to reopen: ' + (err.message || ''))
    }
  }

  const isOverdue = (req: HrServiceRequest): boolean => {
    if (!req.slaDue) return false
    return new Date(req.slaDue) < new Date() && req.status !== 'RESOLVED' && req.status !== 'CLOSED'
  }

  const viewRequest = async (req: HrServiceRequest) => {
    setSelectedRequest(req)
    setViewDrawerOpen(true)
    try {
      const { data } = await hrServiceQueueApi.getComments(req.id)
      setComments(data)
    } catch (err: any) {
      message.error('Failed to load comments: ' + (err.message || ''))
    }
  }

  const addComment = async () => {
    if (!selectedRequest || !commentBody.trim()) return
    try {
      await hrServiceQueueApi.addComment(selectedRequest.id, commentBody, isInternalComment)
      message.success('Comment added')
      setCommentBody('')
      setIsInternalComment(false)
      const { data } = await hrServiceQueueApi.getComments(selectedRequest.id)
      setComments(data)
    } catch (err: any) {
      message.error('Failed to add comment: ' + (err.message || ''))
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
      render: (p: any) => <Tag color={REQUEST_PRIORITY_COLOR[p as ServiceRequestPriority]}>{p}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: any) => <Tag color={REQUEST_STATUS_COLOR[s as ServiceRequestStatus]}>{s}</Tag>,
    },
    {
      title: 'SLA Due',
      dataIndex: 'slaDue',
      width: 120,
      render: (d, rec) => (
        <span style={{ color: isOverdue(rec) ? 'red' : 'inherit', fontWeight: isOverdue(rec) ? 'bold' : 'normal' }}>
          {d ? new Date(d).toLocaleDateString() : '—'}
        </span>
      ),
    },
    {
      title: 'Assigned To',
      dataIndex: 'assignedToUsername',
      width: 120,
      render: (u) => u || '—',
    },
    {
      title: 'Actions',
      width: 320,
      render: (_, rec) => (
        <Space size="small">
          <Button size="small" type="link" onClick={() => viewRequest(rec)}>View</Button>
          <Button size="small" onClick={() => { setSelectedRequest(rec); setAction('reassign'); }}>Reassign</Button>
          {rec.status === 'OPEN' && (
            <>
              <Button size="small" onClick={() => handleStart(rec.id)}>Start</Button>
              <Button size="small" onClick={() => { setSelectedRequest(rec); setAction('assign'); }}>Assign</Button>
            </>
          )}
          {rec.status === 'IN_PROGRESS' && (
            <Button size="small" type="primary" onClick={() => { setSelectedRequest(rec); setAction('resolve'); }}>
              Resolve
            </Button>
          )}
          {rec.status === 'RESOLVED' && (
            <>
              <Button size="small" onClick={() => handleClose(rec.id)}>Close</Button>
              <Button size="small" onClick={() => handleReopen(rec.id)}>Reopen</Button>
            </>
          )}
          {rec.status === 'CLOSED' && (
            <Button size="small" onClick={() => handleReopen(rec.id)}>Reopen</Button>
          )}
        </Space>
      ),
    },
  ]

  // Queue counts for tabs
  const queueCounts = queues.map(q => ({
    queue: q,
    count: requests.filter(r => r.queueId === q.id).length,
  }))
  const unassignedCount = requests.filter(r => !r.queueId).length

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>HR Service Queue</Typography.Title>}>
      {/* Queue Tabs */}
      <Space style={{ marginBottom: 16 }} wrap>
        <Button
          type={!selectedQueueId ? 'primary' : 'default'}
          onClick={() => setSelectedQueueId(undefined)}
        >
          All ({requests.length})
        </Button>
        {queueCounts.map(({ queue, count }) => (
          <Button
            key={queue.id}
            type={selectedQueueId === queue.id ? 'primary' : 'default'}
            onClick={() => setSelectedQueueId(queue.id)}
          >
            {queue.name} ({count})
          </Button>
        ))}
        <Button
          type={selectedQueueId === 'unassigned' ? 'primary' : 'default'}
          onClick={() => setSelectedQueueId('unassigned')}
        >
          Unassigned ({unassignedCount})
        </Button>
      </Space>

      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          style={{ width: 180 }}
          placeholder="Filter by category"
          allowClear
          value={categoryFilter}
          onChange={setCategoryFilter}
        >
          <Select.Option value="SALARY_CERT">Salary Certificate</Select.Option>
          <Select.Option value="EMPLOYMENT_LETTER">Employment Letter</Select.Option>
          <Select.Option value="PAYROLL_INQUIRY">Payroll Inquiry</Select.Option>
          <Select.Option value="POLICY_QUESTION">Policy Question</Select.Option>
          <Select.Option value="GRIEVANCE">Grievance</Select.Option>
          <Select.Option value="OTHER">Other</Select.Option>
        </Select>
        <Select
          style={{ width: 150 }}
          placeholder="Filter by status"
          allowClear
          value={statusFilter}
          onChange={setStatusFilter}
        >
          <Select.Option value="OPEN">Open</Select.Option>
          <Select.Option value="IN_PROGRESS">In Progress</Select.Option>
          <Select.Option value="RESOLVED">Resolved</Select.Option>
          <Select.Option value="CLOSED">Closed</Select.Option>
        </Select>
        <Checkbox checked={overdueOnly} onChange={(e) => setOverdueOnly(e.target.checked)}>
          Overdue only
        </Checkbox>
      </Space>
      <Table
        loading={loading}
        dataSource={selectedQueueId === 'unassigned' ? requests.filter(r => !r.queueId) : requests}
        columns={columns}
        rowKey="id"
        pagination={{ pageSize: 20 }}
      />

      {/* Assign Modal */}
      <Modal
        title="Assign Request"
        open={action === 'assign'}
        onCancel={() => { setAction(null); form.resetFields(); }}
        onOk={handleAssign}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="assignToUsername"
            label="Assign To (username)"
            rules={[{ required: true, message: 'Please enter username' }]}
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      {/* Resolve Modal */}
      <Modal
        title="Resolve Request"
        open={action === 'resolve'}
        onCancel={() => { setAction(null); form.resetFields(); }}
        onOk={handleResolve}
        width={600}
      >
        {selectedRequest && (
          <div style={{ marginBottom: 16 }}>
            <p><strong>Request:</strong> {selectedRequest.requestNo}</p>
            <p><strong>Subject:</strong> {selectedRequest.subject}</p>
            {selectedRequest.description && (
              <p><strong>Description:</strong> {selectedRequest.description}</p>
            )}
          </div>
        )}
        <Form form={form} layout="vertical">
          <Form.Item
            name="resolutionNotes"
            label="Resolution Notes"
            rules={[{ required: true, message: 'Please enter resolution notes' }]}
          >
            <TextArea rows={4} maxLength={4000} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Reassign Queue Modal */}
      <Modal
        title="Reassign to Queue"
        open={action === 'reassign'}
        onCancel={() => { setAction(null); form.resetFields(); }}
        onOk={handleReassign}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="queueId"
            label="Queue"
            rules={[{ required: true, message: 'Please select a queue' }]}
          >
            <Select>
              {queues.map(q => (
                <Select.Option key={q.id} value={q.id}>
                  {q.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      {/* View Drawer with Comments */}
      <Drawer
        title="Request Details"
        placement="right"
        width="50%"
        open={viewDrawerOpen}
        onClose={() => setViewDrawerOpen(false)}
      >
        {selectedRequest && (
          <div>
            <Typography.Title level={5}>{selectedRequest.requestNo}</Typography.Title>
            <Space style={{ marginBottom: 16 }}>
              <Tag color={REQUEST_CATEGORY_COLOR[selectedRequest.category]}>
                {selectedRequest.category.replace(/_/g, ' ')}
              </Tag>
              <Tag color={REQUEST_STATUS_COLOR[selectedRequest.status]}>{selectedRequest.status}</Tag>
              <Tag color={REQUEST_PRIORITY_COLOR[selectedRequest.priority]}>{selectedRequest.priority}</Tag>
            </Space>
            <p><strong>Subject:</strong> {selectedRequest.subject}</p>
            {selectedRequest.description && (
              <p><strong>Description:</strong> {selectedRequest.description}</p>
            )}
            {selectedRequest.assignedToUsername && (
              <p><strong>Assigned To:</strong> {selectedRequest.assignedToUsername}</p>
            )}
            {selectedRequest.slaDue && (
              <p><strong>SLA Due:</strong> {new Date(selectedRequest.slaDue).toLocaleString()}</p>
            )}
            {selectedRequest.resolutionNotes && (
              <p><strong>Resolution:</strong> {selectedRequest.resolutionNotes}</p>
            )}

            <Divider>Comments</Divider>
            <List
              dataSource={comments}
              renderItem={(comment) => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <Space>
                        <span>{comment.authorUsername}</span>
                        {comment.isInternal && <Tag color="orange">Internal</Tag>}
                        <span style={{ fontSize: '12px', color: '#999' }}>
                          {new Date(comment.createdAt).toLocaleString()}
                        </span>
                      </Space>
                    }
                    description={comment.body}
                  />
                </List.Item>
              )}
            />

            <Divider />
            <Space direction="vertical" style={{ width: '100%' }}>
              <TextArea
                rows={3}
                value={commentBody}
                onChange={(e) => setCommentBody(e.target.value)}
                placeholder="Add a comment..."
                maxLength={4000}
              />
              <Space>
                <Switch
                  checked={isInternalComment}
                  onChange={setIsInternalComment}
                  checkedChildren="Internal"
                  unCheckedChildren="Public"
                />
                <Button type="primary" onClick={addComment}>Add Comment</Button>
              </Space>
            </Space>
          </div>
        )}
      </Drawer>
    </Card>
  )
}
