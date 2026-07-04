// HCM_16 M418 — Career dashboard: interests + dev plans + mentoring + paths + job recommendations.
// Employee self-service (access-scoped: own dashboard only).

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Row,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import { api } from '../../api/client'

const { Title } = Typography

interface DashboardData {
  interests: InterestInfo[]
  devPlans: DevPlanInfo[]
  mentoring: MentoringInfo[]
  careerPaths: CareerPathMatch[]
  jobRecommendations: JobRecommendation[]
}

interface InterestInfo {
  id: string
  targetRole: string
  targetDepartment: string
  targetLocation: string
}

interface DevPlanInfo {
  id: string
  planName: string
  status: string
}

interface MentoringInfo {
  id: string
  mentorId: string
  menteeId: string
  status: string
}

interface CareerPathMatch {
  pathId: string
  pathCode: string
  pathName: string
  toPositionId: string
}

interface JobRecommendation {
  jobId: string
  title: string
  department: string
  score: number
}

export function CareerDashboardPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(true)
  const [data, setData] = useState<DashboardData | null>(null)
  const [employeeId, setEmployeeId] = useState<string | null>(null)
  const [addInterestOpen, setAddInterestOpen] = useState(false)
  const [interestForm] = Form.useForm()
  const [adding, setAdding] = useState(false)

  const load = (empId: string) => {
    setLoading(true)
    api
      .get<DashboardData>('/talent/career-dashboard', { params: { employeeId: empId } })
      .then((r) => setData(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load dashboard'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    // Load own employee ID from /api/self or similar endpoint
    // For simplicity, use a mock or user input
    // In a real app, fetch from /api/self context
    const mockEmpId = 'YOUR_EMPLOYEE_UUID' // Replace with actual context
    setEmployeeId(mockEmpId)
    // load(mockEmpId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const submitInterest = async () => {
    if (!employeeId) return
    const v = await interestForm.validateFields()
    setAdding(true)
    try {
      await api.post('/talent/interests', { employeeId, ...v })
      message.success('Interest added')
      setAddInterestOpen(false)
      interestForm.resetFields()
      load(employeeId)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Add failed',
      )
    } finally {
      setAdding(false)
    }
  }

  const removeInterest = async (id: string) => {
    try {
      await api.delete(`/talent/interests/${id}`)
      message.success('Interest removed')
      if (employeeId) load(employeeId)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Remove failed',
      )
    }
  }

  if (loading || !data) {
    return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3}>My Career Dashboard</Title>

      <Row gutter={16}>
        <Col span={12}>
          <Card
            title="My Career Interests"
            extra={<Button size="small" onClick={() => setAddInterestOpen(true)}>Add</Button>}
          >
            {data.interests.length === 0 ? (
              <Empty description="No interests yet" />
            ) : (
              <List
                dataSource={data.interests}
                renderItem={(item) => (
                  <List.Item
                    actions={[
                      <Button size="small" danger onClick={() => removeInterest(item.id)}>
                        Remove
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      title={item.targetRole ?? '—'}
                      description={`${item.targetDepartment ?? '—'} | ${item.targetLocation ?? '—'}`}
                    />
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card title="My Development Plans">
            {data.devPlans.length === 0 ? (
              <Empty description="No dev plans yet" />
            ) : (
              <List
                dataSource={data.devPlans}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta
                      title={item.planName}
                      description={<Tag color="blue">{item.status}</Tag>}
                    />
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Card title="My Mentoring">
            {data.mentoring.length === 0 ? (
              <Empty description="No mentoring relationships" />
            ) : (
              <List
                dataSource={data.mentoring}
                renderItem={(item) => (
                  <List.Item>
                    <Tag color={item.status === 'ACTIVE' ? 'green' : 'default'}>{item.status}</Tag>
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card title="Matching Career Paths">
            {data.careerPaths.length === 0 ? (
              <Empty description="No matching paths from your current position" />
            ) : (
              <List
                dataSource={data.careerPaths}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta
                      title={`${item.pathCode} — ${item.pathName}`}
                      description={`Next step: ${item.toPositionId.slice(0, 8)}…`}
                    />
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
      </Row>

      <Card title="Recommended Internal Jobs">
        {data.jobRecommendations.length === 0 ? (
          <Empty description="No job recommendations yet — add career interests to see matches" />
        ) : (
          <List
            dataSource={data.jobRecommendations}
            renderItem={(item) => (
              <List.Item>
                <List.Item.Meta
                  title={item.title}
                  description={item.department ?? '—'}
                />
                <Tag color="green">Score: {item.score}</Tag>
              </List.Item>
            )}
          />
        )}
      </Card>

      <Modal
        open={addInterestOpen}
        title="Add Career Interest"
        onCancel={() => setAddInterestOpen(false)}
        onOk={submitInterest}
        confirmLoading={adding}
        okText="Add"
      >
        <Form form={interestForm} layout="vertical">
          <Form.Item name="targetRole" label="Target Role" rules={[{ required: true }]}>
            <Input placeholder="Senior Analyst, Manager, etc." />
          </Form.Item>
          <Form.Item name="targetDepartment" label="Target Department">
            <Input placeholder="Finance, Marketing, IT" />
          </Form.Item>
          <Form.Item name="targetLocation" label="Target Location">
            <Input placeholder="Baku, Regional Office" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
