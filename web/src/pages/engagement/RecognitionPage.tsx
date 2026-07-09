// M478 — Recognition/kudos (public wall + send/receive).

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Tabs,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import { recognitionApi, type RecognitionWallItem, type EmployeeRecognition, type RecognitionValueTag } from '../../api/engagement'
import { employeesApi, type Employee } from '../../api/employees'

dayjs.extend(relativeTime)

const VALUE_TAG_COLORS: Record<RecognitionValueTag, string> = {
  TEAMWORK: 'blue',
  INNOVATION: 'purple',
  EXCELLENCE: 'gold',
  CUSTOMER_FOCUS: 'green',
  LEADERSHIP: 'red',
}

const VALUE_TAGS: RecognitionValueTag[] = ['TEAMWORK', 'INNOVATION', 'EXCELLENCE', 'CUSTOMER_FOCUS', 'LEADERSHIP']

export function RecognitionPage() {
  const { message } = AntdApp.useApp()
  const [wall, setWall] = useState<RecognitionWallItem[]>([])
  const [sent, setSent] = useState<EmployeeRecognition[]>([])
  const [received, setReceived] = useState<EmployeeRecognition[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm()

  const loadWall = () => {
    recognitionApi.publicWall().then(setWall).catch(() => {})
  }

  const loadMy = () => {
    Promise.all([recognitionApi.mySent(), recognitionApi.myReceived()])
      .then(([s, r]) => {
        setSent(s)
        setReceived(r)
      })
      .catch(() => {})
  }

  useEffect(() => {
    setLoading(true)
    Promise.all([recognitionApi.publicWall(), recognitionApi.mySent(), recognitionApi.myReceived(), employeesApi.list({ size: 500 })])
      .then(([wall, sent, received, empResp]) => {
        setWall(wall)
        setSent(sent)
        setReceived(received)
        setEmployees(empResp.content)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openGive = () => {
    form.resetFields()
    form.setFieldsValue({ visibility: 'PUBLIC' })
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      await recognitionApi.send(values)
      message.success('Recognition sent')
      setModalOpen(false)
      loadWall()
      loadMy()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Send failed')
    }
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card title="Recognition Wall" extra={<Button type="primary" onClick={openGive}>Give Recognition</Button>} loading={loading}>
        <Row gutter={[16, 16]}>
          {wall.map((item) => (
            <Col key={item.id} xs={24} sm={12} md={8}>
              <Card size="small">
                <Space direction="vertical" size="small" style={{ width: '100%' }}>
                  <Typography.Text strong>{item.fromName} → {item.toName}</Typography.Text>
                  <Tag color={VALUE_TAG_COLORS[item.valueTag]}>{item.valueTag}</Tag>
                  <Typography.Paragraph>{item.message}</Typography.Paragraph>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {dayjs(item.createdAt).fromNow()}
                  </Typography.Text>
                </Space>
              </Card>
            </Col>
          ))}
        </Row>
      </Card>

      <Card title="My Recognition">
        <Tabs
          items={[
            {
              key: 'sent',
              label: `Sent (${sent.length})`,
              children: (
                <Space direction="vertical" style={{ width: '100%' }}>
                  {sent.map((r) => (
                    <Card key={r.id} size="small">
                      <Tag color={VALUE_TAG_COLORS[r.valueTag]}>{r.valueTag}</Tag>
                      <Typography.Text>{r.message}</Typography.Text>
                      <Typography.Text type="secondary" style={{ fontSize: 11, marginLeft: 8 }}>
                        ({dayjs(r.createdAt).format('YYYY-MM-DD')})
                      </Typography.Text>
                    </Card>
                  ))}
                </Space>
              ),
            },
            {
              key: 'received',
              label: `Received (${received.length})`,
              children: (
                <Space direction="vertical" style={{ width: '100%' }}>
                  {received.map((r) => (
                    <Card key={r.id} size="small">
                      <Tag color={VALUE_TAG_COLORS[r.valueTag]}>{r.valueTag}</Tag>
                      <Typography.Text>{r.message}</Typography.Text>
                      <Typography.Text type="secondary" style={{ fontSize: 11, marginLeft: 8 }}>
                        ({dayjs(r.createdAt).format('YYYY-MM-DD')})
                      </Typography.Text>
                    </Card>
                  ))}
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title="Give Recognition"
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="toEmployeeId" label="To Employee" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="children">
              {employees.map((emp) => (
                <Select.Option key={emp.id} value={emp.id}>
                  {emp.firstName} {emp.lastName} ({emp.employeeNo})
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="valueTag" label="Value" rules={[{ required: true }]}>
            <Select>
              {VALUE_TAGS.map((tag) => (
                <Select.Option key={tag} value={tag}>
                  {tag}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="message" label="Message" rules={[{ required: true }]}>
            <Input.TextArea rows={4} maxLength={1000} />
          </Form.Item>
          <Form.Item name="visibility" label="Visibility" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="PUBLIC">Public</Select.Option>
              <Select.Option value="PRIVATE">Private</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
