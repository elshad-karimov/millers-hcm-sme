import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import { api } from '../api/client'
import dayjs from 'dayjs'

interface SignerDTO {
  id: string
  requestId: string
  username: string
  employeeId?: string
  status: string
  signedAt?: string
  declineReason?: string
}

interface SignatureRequestDTO {
  id: string
  title: string
  employeeDocumentId?: string
  letterRequestId?: string
  attachmentId?: string
  status: string
  provider: string
  createdAt: string
  createdBy: string
  signers: SignerDTO[]
}

const SIGNER_STATUS_COLOR: Record<string, string> = {
  PENDING: 'orange',
  SIGNED: 'green',
  DECLINED: 'red',
}

const REQUEST_STATUS_COLOR: Record<string, string> = {
  PENDING: 'processing',
  COMPLETED: 'success',
  CANCELLED: 'default',
  PARTIAL: 'warning',
}

export function SignaturesPage() {
  const { message } = AntdApp.useApp()
  const [requests, setRequests] = useState<SignatureRequestDTO[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)

  const [form] = Form.useForm()

  const fetchRequests = async () => {
    setLoading(true)
    try {
      const { data } = await api.get('/documents/signatures')
      setRequests(data)
    } catch (err: any) {
      message.error(err.message || 'Failed to load signature requests')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchRequests()
  }, [])

  const handleCreate = async (values: any) => {
    try {
      await api.post('/documents/signatures', {
        title: values.title,
        employeeDocumentId: values.employeeDocumentId || null,
        letterRequestId: values.letterRequestId || null,
        attachmentId: values.attachmentId || null,
        signerUsernames: values.signerUsernames,
      })
      message.success('Signature request created')
      setCreateOpen(false)
      form.resetFields()
      fetchRequests()
    } catch (err: any) {
      message.error(err.message || 'Failed to create signature request')
    }
  }

  const handleCancel = async (requestId: string) => {
    try {
      await api.post(`/documents/signatures/${requestId}/cancel`, {})
      message.success('Signature request cancelled')
      fetchRequests()
    } catch (err: any) {
      message.error(err.message || 'Failed to cancel request')
    }
  }

  const columns: ColumnsType<SignatureRequestDTO> = [
    {
      title: 'Title',
      dataIndex: 'title',
      key: 'title',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: string) => <Tag color={REQUEST_STATUS_COLOR[status]}>{status}</Tag>,
    },
    {
      title: 'Signers',
      key: 'signers',
      width: 300,
      render: (_, rec) => (
        <Space direction="vertical" size="small">
          {rec.signers.map((signer) => (
            <Space key={signer.id} size="small">
              <Typography.Text style={{ minWidth: 120 }}>{signer.username}</Typography.Text>
              <Tag color={SIGNER_STATUS_COLOR[signer.status]}>{signer.status}</Tag>
              {signer.signedAt && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {dayjs(signer.signedAt).format('YYYY-MM-DD HH:mm')}
                </Typography.Text>
              )}
              {signer.declineReason && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  ({signer.declineReason})
                </Typography.Text>
              )}
            </Space>
          ))}
        </Space>
      ),
    },
    {
      title: 'Progress',
      key: 'progress',
      width: 100,
      align: 'center',
      render: (_, rec) => {
        const signed = rec.signers.filter((s) => s.status === 'SIGNED').length
        const total = rec.signers.length
        return `${signed}/${total}`
      },
    },
    {
      title: 'Created',
      key: 'created',
      width: 180,
      render: (_, rec) => (
        <Space direction="vertical" size={0}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {dayjs(rec.createdAt).format('YYYY-MM-DD HH:mm')}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            by {rec.createdBy}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      width: 100,
      fixed: 'right',
      render: (_, rec) =>
        rec.status === 'PENDING' ? (
          <Button
            type="link"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleCancel(rec.id)}
          >
            Cancel
          </Button>
        ) : null,
    },
  ]

  return (
    <Card
      title="Signature Requests"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          New Request
        </Button>
      }
    >
      <Table
        dataSource={requests}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 20 }}
        scroll={{ x: 1200 }}
      />

      {/* Create Modal */}
      <Modal
        title="New Signature Request"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item
            name="title"
            label="Title"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="e.g. Employment Contract Signature" />
          </Form.Item>
          <Form.Item
            name="target"
            label="Target Document"
            help="Specify ONE of: Employee Document ID, Letter Request ID, or Attachment ID"
          >
            <Input.Group>
              <Form.Item name="employeeDocumentId" noStyle>
                <Input
                  placeholder="Employee Document ID (UUID)"
                  style={{ width: '100%', marginBottom: 8 }}
                />
              </Form.Item>
              <Form.Item name="letterRequestId" noStyle>
                <Input
                  placeholder="Letter Request ID (UUID)"
                  style={{ width: '100%', marginBottom: 8 }}
                />
              </Form.Item>
              <Form.Item name="attachmentId" noStyle>
                <Input placeholder="Attachment ID (UUID)" style={{ width: '100%' }} />
              </Form.Item>
            </Input.Group>
          </Form.Item>
          <Form.Item
            name="signerUsernames"
            label="Signers (Usernames)"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select mode="tags" placeholder="Enter usernames and press Enter" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
